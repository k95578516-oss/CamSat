package com.example.camsat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.camsat.support.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** One user must never be able to see or change another user's data by guessing or changing an ID. */
class IsolationApiTest extends ApiTestBase {

    @Test
    void otherUsersResourcesAreNotFoundForEveryEndpoint() throws Exception {
        Session alice = register(), bob = register();
        long mission = createMission(alice, missionBody());
        long run = createRun(alice, mission);
        mvc.perform(auth(post("/api/runs/" + run + "/start"), alice)).andExpect(status().isAccepted());
        waitForStatus(alice, run, Set.of("COMPLETED"), Duration.ofSeconds(60));

        String[] gets = {"/api/missions/" + mission, "/api/missions/" + mission + "/runs", "/api/runs/" + run,
                "/api/runs/" + run + "/telemetry", "/api/runs/" + run + "/telemetry/latest-reading", "/api/runs/" + run + "/events",
                "/api/runs/" + run + "/events/latest", "/api/runs/" + run + "/events/count", "/api/runs/" + run + "/recovery",
                "/api/runs/" + run + "/report"};
        for (String url : gets) mvc.perform(auth(get(url), bob)).andExpect(status().isNotFound());

        mvc.perform(auth(put("/api/missions/" + mission), bob).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(missionBody()))).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/missions/" + mission), bob)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/missions/" + mission + "/runs"), bob)).andExpect(status().isNotFound());
        for (String action : new String[]{"start", "pause", "resume", "cancel"}) mvc.perform(auth(post("/api/runs/" + run + "/" + action), bob)).andExpect(status().isNotFound());

        // Alice's data is untouched
        assertThat(getJson(alice, "/api/runs/" + run).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(getJson(alice, "/api/missions/" + mission).get("name").asText()).isEqualTo("Alpha");
    }

    @Test
    void listsDashboardAnalyticsAndRecentOnlyContainTheCallersData() throws Exception {
        Session alice = register(), bob = register();
        long mission = createMission(alice, missionBody());
        long run = createRun(alice, mission);
        mvc.perform(auth(post("/api/runs/" + run + "/start"), alice)).andExpect(status().isAccepted());
        waitForStatus(alice, run, Set.of("COMPLETED"), Duration.ofSeconds(60));

        assertThat(getJson(bob, "/api/missions")).isEmpty();
        assertThat(getJson(bob, "/api/runs/recent")).isEmpty();
        JsonNode dash = getJson(bob, "/api/dashboard");
        assertThat(dash.get("totalMissions").asLong()).isZero();
        assertThat(dash.get("totalSimulationRuns").asLong()).isZero();
        assertThat(dash.get("totalTelemetryReadings").asLong()).isZero();
        assertThat(dash.get("recentRuns")).isEmpty();
        assertThat(getJson(bob, "/api/analytics").get("totalRuns").asLong()).isZero();

        JsonNode aliceDash = getJson(alice, "/api/dashboard");
        assertThat(aliceDash.get("totalMissions").asLong()).isEqualTo(1);
        assertThat(aliceDash.get("completedRuns").asLong()).isEqualTo(1);
        assertThat(aliceDash.get("totalTelemetryReadings").asLong()).isGreaterThan(30);
    }

    @Test
    void aUserIdInTheBodyOrQueryCannotImpersonateAnotherUser() throws Exception {
        Session alice = register(), bob = register();
        long mission = createMission(alice, missionBody());
        var body = missionBody(); body.put("userId", bob.userId()); body.put("id", 1);
        long mine = om.readTree(mvc.perform(auth(post("/api/missions").param("userId", String.valueOf(bob.userId())), alice)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertThat(getJson(alice, "/api/missions/" + mine).get("name").asText()).isEqualTo("Alpha");
        mvc.perform(auth(get("/api/missions/" + mine), bob)).andExpect(status().isNotFound());
        assertThat(getJson(bob, "/api/missions")).isEmpty();
        assertThat(mission).isNotEqualTo(mine);
    }
}

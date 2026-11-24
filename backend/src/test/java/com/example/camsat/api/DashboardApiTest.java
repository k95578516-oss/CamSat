package com.example.camsat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.camsat.support.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DashboardApiTest extends ApiTestBase {

    @Test
    void emptyAccountReturnsValidZeroesAndNullAverages() throws Exception {
        Session s = register();
        JsonNode d = getJson(s, "/api/dashboard");
        assertThat(d.fieldNames()).toIterable().containsExactlyInAnyOrder("totalMissions", "totalSimulationRuns", "completedRuns", "failedRuns",
                "activeRuns", "createdRuns", "totalTelemetryReadings", "runsByStatus", "recentRuns");
        assertThat(d.get("totalMissions").asLong()).isZero();
        assertThat(d.get("runsByStatus").size()).isZero();
        assertThat(d.get("recentRuns")).isEmpty();
        JsonNode a = getJson(s, "/api/analytics");
        assertThat(a.get("totalRuns").asLong()).isZero();
        assertThat(a.get("successRatePercent").asDouble()).isZero();
        assertThat(a.get("averageLandingTimeSeconds").isNull()).isTrue();
        assertThat(a.get("averageLandingDriftMeters").isNull()).isTrue();
        assertThat(a.get("averageMaximumDescentSpeedMps").isNull()).isTrue();
    }

    @Test
    void aggregatesCountEveryStatusAndIgnoreIncompleteRunsInAverages() throws Exception {
        Session s = register();
        long good = createRun(s, createMission(s, missionBody()));
        Map<String, Object> bad = missionBody(); bad.put("payloadMassKg", 5.0); bad.put("parachuteDiameterM", 0.1); bad.put("dragCoefficient", 0.2);
        long failing = createRun(s, createMission(s, bad));
        long created = createRun(s, createMission(s, missionBody()));
        long cancelled = createRun(s, createMission(s, missionBody()));
        mvc.perform(auth(post("/api/runs/" + cancelled + "/cancel"), s)).andExpect(status().isOk());
        mvc.perform(auth(post("/api/runs/" + good + "/start"), s)).andExpect(status().isAccepted());
        mvc.perform(auth(post("/api/runs/" + failing + "/start"), s)).andExpect(status().isAccepted());
        JsonNode goodDone = waitForStatus(s, good, Set.of("COMPLETED", "FAILED"), Duration.ofSeconds(60));
        waitForStatus(s, failing, Set.of("COMPLETED", "FAILED"), Duration.ofSeconds(60));

        JsonNode d = getJson(s, "/api/dashboard");
        assertThat(d.get("totalMissions").asLong()).isEqualTo(4);
        assertThat(d.get("totalSimulationRuns").asLong()).isEqualTo(4);
        assertThat(d.get("completedRuns").asLong()).isEqualTo(1);
        assertThat(d.get("failedRuns").asLong()).isEqualTo(1);
        assertThat(d.get("createdRuns").asLong()).isEqualTo(1);
        assertThat(d.get("activeRuns").asLong()).isZero();
        assertThat(d.get("runsByStatus").get("CANCELLED").asLong()).isEqualTo(1);
        assertThat(d.get("recentRuns").size()).isEqualTo(4);
        assertThat(d.get("recentRuns").get(0).get("runId").asLong()).isEqualTo(cancelled);
        assertThat(d.get("recentRuns").get(0).get("missionName").asText()).isEqualTo("Alpha");
        long tel = getJson(s, "/api/runs/" + good + "/telemetry").size() + getJson(s, "/api/runs/" + failing + "/telemetry").size();
        assertThat(d.get("totalTelemetryReadings").asLong()).isEqualTo(tel);

        JsonNode a = getJson(s, "/api/analytics");
        assertThat(a.get("totalRuns").asLong()).isEqualTo(4);
        assertThat(a.get("successRatePercent").asDouble()).isEqualTo(50.0);   // 1 completed / (1 completed + 1 failed)
        assertThat(a.get("averageLandingTimeSeconds").asDouble()).isEqualTo(goodDone.get("landingTimeSeconds").asDouble());
        assertThat(a.get("averageLandingDriftMeters").asDouble()).isEqualTo(goodDone.get("landingDriftM").asDouble());
        assertThat(a.get("averageMaximumDescentSpeedMps").asDouble()).isBetween(3.0, 40.0);
        assertThat(created).isNotEqualTo(good);
    }
}

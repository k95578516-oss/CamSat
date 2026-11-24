package com.example.camsat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.camsat.support.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Paced simulation (15 ms per simulated second) so cancel/pause reliably arrive while a run is still executing. */
@TestPropertySource(properties = "camsat.simulation.sample-delay-ms=15")
class SimulationControlTest extends ApiTestBase {
    private static final Duration WAIT = Duration.ofSeconds(120);

    private long startedRun(Session s, Map<String, Object> mission) throws Exception {
        long run = createRun(s, createMission(s, mission));
        mvc.perform(auth(post("/api/runs/" + run + "/start"), s)).andExpect(status().isAccepted());
        return run;
    }

    private Map<String, Integer> eventCounts(Session s, long run) throws Exception {
        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode e : getJson(s, "/api/runs/" + run + "/events")) counts.merge(e.get("eventType").asText(), 1, Integer::sum);
        return counts;
    }

    @Test
    void cancellingARunningRunStopsItAndWritesNoMoreTelemetry() throws Exception {
        Session s = register();
        Map<String, Object> m = missionBody();
        m.put("releaseAltitudeM", 10000.0);       // long flight so it is still running when cancelled
        long run = startedRun(s, m);
        long end = System.currentTimeMillis() + 30_000;
        while (getJson(s, "/api/runs/" + run + "/telemetry").isEmpty() && System.currentTimeMillis() < end) Thread.sleep(10);
        mvc.perform(auth(post("/api/runs/" + run + "/cancel"), s)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        int atCancel = getJson(s, "/api/runs/" + run + "/telemetry").size();
        Thread.sleep(400);
        assertThat(getJson(s, "/api/runs/" + run + "/telemetry")).hasSize(atCancel);
        JsonNode r = getJson(s, "/api/runs/" + run);
        assertThat(r.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(r.get("endedAt").isNull()).isFalse();
        Map<String, Integer> counts = eventCounts(s, run);
        assertThat(counts.get("SIMULATION_CANCELLED")).isEqualTo(1);
        assertThat(counts).doesNotContainKeys("SIMULATION_COMPLETED", "RECOVERY_COMPLETED");
        assertThat(getJson(s, "/api/runs/" + run + "/recovery").get("recoveryStatus").asText()).isEqualTo("NOT_STARTED");
        assertThat(getJson(s, "/api/runs/" + run + "/report").get("status").asText()).isEqualTo("CANCELLED");
        mvc.perform(auth(post("/api/runs/" + run + "/cancel"), s)).andExpect(status().isConflict());
        mvc.perform(auth(post("/api/runs/" + run + "/start"), s)).andExpect(status().isConflict());
    }

    @Test
    void pauseHaltsTelemetryAndResumeContinuesToCompletion() throws Exception {
        Session s = register();
        Map<String, Object> m = missionBody();
        m.put("releaseAltitudeM", 3000.0);
        long run = startedRun(s, m);
        long end = System.currentTimeMillis() + 30_000;
        while (getJson(s, "/api/runs/" + run + "/telemetry").size() < 3 && System.currentTimeMillis() < end) Thread.sleep(10);
        mvc.perform(auth(post("/api/runs/" + run + "/pause"), s)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(auth(post("/api/runs/" + run + "/pause"), s)).andExpect(status().isConflict());
        Thread.sleep(300);                       // let any batch already in flight settle
        int paused = getJson(s, "/api/runs/" + run + "/telemetry").size();
        Thread.sleep(400);
        assertThat(getJson(s, "/api/runs/" + run + "/telemetry")).hasSize(paused);
        assertThat(getJson(s, "/api/runs/" + run).get("status").asText()).isEqualTo("PAUSED");

        mvc.perform(auth(post("/api/runs/" + run + "/resume"), s)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RUNNING"));
        waitForStatus(s, run, Set.of("COMPLETED", "FAILED"), Duration.ofSeconds(120));
        Map<String, Integer> counts = eventCounts(s, run);
        assertThat(counts.get("SIMULATION_PAUSED")).isEqualTo(1);
        assertThat(counts.get("SIMULATION_RESUMED")).isEqualTo(1);
        assertThat(counts.get("SIMULATION_COMPLETED")).isEqualTo(1);
    }
}

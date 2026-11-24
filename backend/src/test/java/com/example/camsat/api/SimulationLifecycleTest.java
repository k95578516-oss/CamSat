package com.example.camsat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.camsat.support.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class SimulationLifecycleTest extends ApiTestBase {
    private static final Duration WAIT = Duration.ofSeconds(60);

    private long startedRun(Session s, Map<String, Object> mission) throws Exception {
        long run = createRun(s, createMission(s, mission));
        mvc.perform(auth(post("/api/runs/" + run + "/start"), s)).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("RUNNING"));
        return run;
    }

    private Map<String, Integer> eventCounts(Session s, long run) throws Exception {
        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode e : getJson(s, "/api/runs/" + run + "/events")) counts.merge(e.get("eventType").asText(), 1, Integer::sum);
        return counts;
    }

    @Test
    void createdRunHasNoDataYet() throws Exception {
        Session s = register();
        long run = createRun(s, createMission(s, missionBody()));
        JsonNode r = getJson(s, "/api/runs/" + run);
        assertThat(r.get("status").asText()).isEqualTo("CREATED");
        assertThat(r.get("startedAt").isNull()).isTrue();
        assertThat(getJson(s, "/api/runs/" + run + "/telemetry")).isEmpty();
        assertThat(getJson(s, "/api/runs/" + run + "/events")).isEmpty();
        assertThat(getJson(s, "/api/runs/" + run + "/events/count").asLong()).isZero();
        mvc.perform(auth(get("/api/runs/" + run + "/telemetry/latest-reading"), s)).andExpect(status().isNotFound());
        assertThat(getJson(s, "/api/runs/" + run + "/recovery").get("recoveryStatus").asText()).isEqualTo("PENDING");
        JsonNode report = getJson(s, "/api/runs/" + run + "/report");
        assertThat(report.get("telemetryReadingCount").asLong()).isZero();
        assertThat(report.get("maximumAltitudeMeters").isNull()).isTrue();
        assertThat(report.get("finalFlightState").isNull()).isTrue();
    }

    @Test
    void completedRunIsConsistentAcrossRunTelemetryEventsRecoveryAndReport() throws Exception {
        Session s = register();
        Map<String, Object> mission = missionBody();
        long run = startedRun(s, mission);
        JsonNode done = waitForStatus(s, run, Set.of("COMPLETED", "FAILED"), WAIT);
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("startedAt").isNull()).isFalse();
        assertThat(done.get("endedAt").isNull()).isFalse();
        double landing = done.get("landingTimeSeconds").asDouble();
        assertThat(landing).isBetween(30.0, 200.0);
        assertThat(done.get("landingDriftM").asDouble()).isBetween(1.0, landing * 3.0 * 1.05);
        assertThat(done.get("progressPercent").asDouble()).isEqualTo(100.0);

        JsonNode tel = getJson(s, "/api/runs/" + run + "/telemetry");
        assertThat(tel.size()).isGreaterThan(30);
        double prevT = -1, prevAlt = Double.MAX_VALUE;
        for (JsonNode t : tel) {
            assertThat(t.get("runId").asLong()).isEqualTo(run);
            assertThat(t.get("elapsedTimeSeconds").asDouble()).isGreaterThanOrEqualTo(prevT);
            assertThat(t.get("altitudeMeters").asDouble()).isBetween(0.0, 300.0).isLessThanOrEqualTo(prevAlt);
            assertThat(t.get("latitude").isNull()).isTrue();     // no launch coordinates exist, so none are invented
            assertThat(t.get("longitude").isNull()).isTrue();
            prevT = t.get("elapsedTimeSeconds").asDouble(); prevAlt = t.get("altitudeMeters").asDouble();
        }
        assertThat(tel.get(0).get("flightState").asText()).isEqualTo("READY");
        JsonNode last = tel.get(tel.size() - 1);
        assertThat(last.get("flightState").asText()).isEqualTo("RECOVERY");
        assertThat(last.get("altitudeMeters").asDouble()).isZero();
        assertThat(getJson(s, "/api/runs/" + run + "/telemetry/latest-reading").get("id").asLong()).isEqualTo(last.get("id").asLong());

        Map<String, Integer> counts = eventCounts(s, run);
        for (String type : List.of("SIMULATION_STARTED", "DESCENT_STARTED", "PARACHUTE_DEPLOYED", "LANDING_DETECTED",
                "RECOVERY_STARTED", "RECOVERY_COMPLETED", "SIMULATION_COMPLETED")) assertThat(counts.get(type)).as(type).isEqualTo(1);
        assertThat(counts).doesNotContainKeys("LOW_BATTERY", "SIMULATION_FAILED", "SIMULATION_CANCELLED");
        assertThat(getJson(s, "/api/runs/" + run + "/events/count").asLong()).isEqualTo(counts.values().stream().mapToInt(Integer::intValue).sum());
        JsonNode events = getJson(s, "/api/runs/" + run + "/events");
        double prev = -1;
        for (JsonNode e : events) { assertThat(e.get("elapsedTimeSeconds").asDouble()).isGreaterThanOrEqualTo(prev); prev = e.get("elapsedTimeSeconds").asDouble(); }
        JsonNode latest = getJson(s, "/api/runs/" + run + "/events/latest");
        assertThat(latest.size()).isLessThanOrEqualTo(5);
        assertThat(latest.get(0).get("eventType").asText()).isEqualTo("SIMULATION_COMPLETED");

        JsonNode rec = getJson(s, "/api/runs/" + run + "/recovery");
        assertThat(rec.get("recoveryStatus").asText()).isEqualTo("COMPLETED");
        assertThat(rec.get("landingDetected").asBoolean() && rec.get("recoveryStarted").asBoolean() && rec.get("recoveryCompleted").asBoolean()).isTrue();
        assertThat(rec.get("landingDriftM").asDouble()).isEqualTo(done.get("landingDriftM").asDouble());

        JsonNode rep = getJson(s, "/api/runs/" + run + "/report");
        assertThat(rep.get("telemetryReadingCount").asLong()).isEqualTo(tel.size());
        assertThat(rep.get("maximumAltitudeMeters").asDouble()).isEqualTo(300.0);
        assertThat(rep.get("finalFlightState").asText()).isEqualTo("RECOVERY");
        assertThat(rep.get("maximumDescentSpeedMps").asDouble()).isBetween(3.0, 40.0);
        assertThat(rep.get("minimumBatteryPercent").asDouble()).isLessThan(100.0).isGreaterThan(90.0);
        assertThat(rep.get("events")).hasSize(events.size());
        assertThat(rep.get("releaseAltitudeM").asDouble()).isEqualTo(300.0);
    }

    @Test
    void startingTwiceIsAConflict() throws Exception {
        Session s = register();
        long run = startedRun(s, missionBody());
        mvc.perform(auth(post("/api/runs/" + run + "/start"), s)).andExpect(status().isConflict());
        waitForStatus(s, run, Set.of("COMPLETED"), WAIT);
        mvc.perform(auth(post("/api/runs/" + run + "/start"), s)).andExpect(status().isConflict());
        assertThat(eventCounts(s, run).get("SIMULATION_STARTED")).isEqualTo(1);
    }

    @Test
    void concurrentStartsExecuteTheRunExactlyOnce() throws Exception {
        Session s = register();
        long run = createRun(s, createMission(s, missionBody()));
        int n = 10;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Callable<Integer> c = () -> { go.await(); return mvc.perform(auth(post("/api/runs/" + run + "/start"), s)).andReturn().getResponse().getStatus(); };
            results.add(pool.submit(c));
        }
        go.countDown();
        int accepted = 0, conflicts = 0;
        for (Future<Integer> f : results) { int code = f.get(); if (code == 202) accepted++; else if (code == 409) conflicts++; }
        pool.shutdown();
        assertThat(accepted).isEqualTo(1);
        assertThat(conflicts).isEqualTo(n - 1);
        waitForStatus(s, run, Set.of("COMPLETED"), WAIT);
        Map<String, Integer> counts = eventCounts(s, run);
        assertThat(counts.get("SIMULATION_STARTED")).isEqualTo(1);
        assertThat(counts.get("PARACHUTE_DEPLOYED")).isEqualTo(1);
        // a run executed twice would interleave two telemetry series
        JsonNode tel = getJson(s, "/api/runs/" + run + "/telemetry");
        long ready = 0; for (JsonNode t : tel) if (t.get("flightState").asText().equals("READY")) ready++;
        assertThat(ready).isEqualTo(1);
    }

    @Test
    void aCreatedRunCanBeCancelledButNotPaused() throws Exception {
        Session s = register();
        long run = createRun(s, createMission(s, missionBody()));
        mvc.perform(auth(post("/api/runs/" + run + "/pause"), s)).andExpect(status().isConflict());
        mvc.perform(auth(post("/api/runs/" + run + "/resume"), s)).andExpect(status().isConflict());
        mvc.perform(auth(post("/api/runs/" + run + "/cancel"), s)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void undersizedParachuteFailsWithAReasonAndNoFalseSuccess() throws Exception {
        Session s = register();
        Map<String, Object> m = missionBody();
        m.put("payloadMassKg", 5.0); m.put("parachuteDiameterM", 0.1); m.put("dragCoefficient", 0.2);
        long run = startedRun(s, m);
        JsonNode done = waitForStatus(s, run, Set.of("COMPLETED", "FAILED"), WAIT);
        assertThat(done.get("status").asText()).isEqualTo("FAILED");
        assertThat(done.get("finalResult").asText()).contains("impact");
        Map<String, Integer> counts = eventCounts(s, run);
        assertThat(counts.get("SIMULATION_FAILED")).isEqualTo(1);
        assertThat(counts).doesNotContainKeys("SIMULATION_COMPLETED", "RECOVERY_COMPLETED", "RECOVERY_STARTED");
        JsonNode rec = getJson(s, "/api/runs/" + run + "/recovery");
        assertThat(rec.get("recoveryStatus").asText()).isEqualTo("FAILED");
        assertThat(rec.get("recoveryCompleted").asBoolean()).isFalse();
        JsonNode rep = getJson(s, "/api/runs/" + run + "/report");
        assertThat(rep.get("status").asText()).isEqualTo("FAILED");
        assertThat(rep.get("finalFlightState").asText()).isEqualTo("LANDED");
    }

    @Test
    void lowStartingBatteryRaisesLowBatteryExactlyOnce() throws Exception {
        Session s = register();
        Map<String, Object> m = missionBody();
        m.put("initialBatteryPercent", 15.0);
        long run = startedRun(s, m);
        waitForStatus(s, run, Set.of("COMPLETED", "FAILED"), WAIT);
        assertThat(eventCounts(s, run).get("LOW_BATTERY")).isEqualTo(1);
    }

    @Test
    void batteryExhaustionFailsTheRunBeforeTouchdown() throws Exception {
        Session s = register();
        Map<String, Object> m = missionBody();
        m.put("releaseAltitudeM", 3000.0); m.put("initialBatteryPercent", 1.0);
        long run = startedRun(s, m);
        JsonNode done = waitForStatus(s, run, Set.of("COMPLETED", "FAILED"), Duration.ofSeconds(120));
        assertThat(done.get("status").asText()).isEqualTo("FAILED");
        assertThat(done.get("finalResult").asText()).containsIgnoringCase("battery");
        assertThat(done.get("landingTimeSeconds").isNull()).isTrue();
        assertThat(getJson(s, "/api/runs/" + run + "/report").get("finalFlightState").asText()).isEqualTo("ABORTED");
    }

    @Test
    void telemetryWindowAndLimitAreHonouredAndBackwardCompatible() throws Exception {
        Session s = register();
        long run = startedRun(s, missionBody());
        waitForStatus(s, run, Set.of("COMPLETED"), WAIT);
        int all = getJson(s, "/api/runs/" + run + "/telemetry").size();
        JsonNode windowed = om.readTree(mvc.perform(auth(get("/api/runs/" + run + "/telemetry").param("fromSeconds", "10").param("toSeconds", "20"), s)).andReturn().getResponse().getContentAsString());
        assertThat(windowed.size()).isBetween(9, 12);
        for (JsonNode t : windowed) assertThat(t.get("elapsedTimeSeconds").asDouble()).isBetween(10.0, 20.0);
        assertThat(om.readTree(mvc.perform(auth(get("/api/runs/" + run + "/telemetry").param("limit", "5"), s)).andReturn().getResponse().getContentAsString())).hasSize(5);
        mvc.perform(auth(get("/api/runs/" + run + "/telemetry").param("limit", "0"), s)).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/runs/" + run + "/telemetry").param("fromSeconds", "20").param("toSeconds", "10"), s)).andExpect(status().isBadRequest());
        assertThat(all).isGreaterThan(30);
    }

    @Test
    void runListsAndRecentAreOrderedAndScoped() throws Exception {
        Session s = register();
        long mission = createMission(s, missionBody());
        long r1 = createRun(s, mission), r2 = createRun(s, mission);
        JsonNode list = getJson(s, "/api/missions/" + mission + "/runs");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("id").asLong()).isEqualTo(r2);
        JsonNode recent = getJson(s, "/api/runs/recent");
        assertThat(recent.get(0).get("id").asLong()).isEqualTo(r2);
        assertThat(recent.get(1).get("id").asLong()).isEqualTo(r1);
    }
}

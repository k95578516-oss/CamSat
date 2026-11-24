package com.example.camsat.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.camsat.domain.*;
import com.example.camsat.repository.*;
import com.example.camsat.service.RunRecovery;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RunRecoveryTest {
    @Autowired RunRecovery recovery;
    @Autowired UserRepository users;
    @Autowired MissionRepository missions;
    @Autowired SimulationRunRepository runs;
    @Autowired FlightEventRepository events;
    @Autowired JdbcTemplate jdbc;

    private SimulationRun runWithStatus(String status) {
        User u = users.save(new User("U", "r" + System.nanoTime() + "@example.com", "h", Role.USER, Instant.now()));
        Mission m = new Mission(u.getId(), Instant.now());
        m.setName("M"); m.setPayloadMassKg(1); m.setReleaseAltitudeM(100); m.setParachuteDiameterM(1); m.setDragCoefficient(1);
        m.setWindSpeedMps(0); m.setWindDirectionDeg(0); m.setInitialBatteryPercent(100);
        SimulationRun r = runs.save(new SimulationRun(missions.save(m).getId(), u.getId(), Instant.now()));
        jdbc.update("update simulation_runs set status = ? where id = ?", status, r.getId());
        return r;
    }

    @Test
    void runsLeftRunningOrPausedByACrashAreClosedAsFailedExactlyOnce() {
        SimulationRun running = runWithStatus("RUNNING"), paused = runWithStatus("PAUSED"), created = runWithStatus("CREATED"), done = runWithStatus("COMPLETED");
        recovery.failOrphanedRuns();
        recovery.failOrphanedRuns(); // idempotent
        for (SimulationRun r : new SimulationRun[]{running, paused}) {
            SimulationRun now = runs.findById(r.getId()).orElseThrow();
            assertThat(now.getStatus()).isEqualTo(SimulationStatus.FAILED);
            assertThat(now.getEndedAt()).isNotNull();
            assertThat(now.getFinalResult()).containsIgnoringCase("restart");
            assertThat(events.countByRunIdAndEventType(r.getId(), FlightEventType.SIMULATION_FAILED)).isEqualTo(1);
        }
        assertThat(runs.findById(created.getId()).orElseThrow().getStatus()).isEqualTo(SimulationStatus.CREATED);
        assertThat(runs.findById(done.getId()).orElseThrow().getStatus()).isEqualTo(SimulationStatus.COMPLETED);
    }
}

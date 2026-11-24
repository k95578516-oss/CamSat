package com.example.camsat.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.camsat.domain.*;
import com.example.camsat.repository.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
/** Regression: migrations V1+V3+V4+V10 must leave elapsed_sim_seconds / landing_drift_m nullable. */
@SpringBootTest
@ActiveProfiles("test")
class RunColumnsNullableTest {
    @Autowired UserRepository users;
    @Autowired MissionRepository missions;
    @Autowired SimulationRunRepository runs;
    @Autowired JdbcTemplate jdbc;

    @Test
    void newRunIsStoredWithNullElapsedAndDrift() {
        User u = users.save(new User("U", "n" + System.nanoTime() + "@example.com", "h", Role.USER, Instant.now()));
        Mission m = new Mission(u.getId(), Instant.now());
        m.setName("M"); m.setPayloadMassKg(1); m.setReleaseAltitudeM(100); m.setParachuteDiameterM(1); m.setDragCoefficient(1);
        m.setWindSpeedMps(0); m.setWindDirectionDeg(0); m.setInitialBatteryPercent(100);
        SimulationRun r = runs.saveAndFlush(new SimulationRun(missions.save(m).getId(), u.getId(), Instant.now()));
        SimulationRun back = runs.findById(r.getId()).orElseThrow();
        assertThat(back.getElapsedSimSeconds()).isNull();
        assertThat(back.getLandingDriftM()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success = false", Integer.class)).isZero();
    }
}
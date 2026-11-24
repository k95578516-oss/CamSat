package com.example.camsat.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.camsat.domain.*;
import com.example.camsat.repository.*;
import com.example.camsat.service.EventRecorder;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class SchemaAndConstraintsTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired MissionRepository missions;
    @Autowired SimulationRunRepository runs;
    @Autowired TelemetryReadingRepository telemetry;
    @Autowired FlightEventRepository events;
    @Autowired EventRecorder recorder;
    @Autowired TransactionTemplate tx;

    private SimulationRun newRun() {
        User u = users.save(new User("U", "c" + System.nanoTime() + "@example.com", "hash", Role.USER, Instant.now()));
        Mission m = new Mission(u.getId(), Instant.now());
        m.setName("M"); m.setPayloadMassKg(1); m.setReleaseAltitudeM(100); m.setParachuteDiameterM(1); m.setDragCoefficient(1);
        m.setWindSpeedMps(0); m.setWindDirectionDeg(0); m.setInitialBatteryPercent(100);
        m = missions.save(m);
        return runs.save(new SimulationRun(m.getId(), u.getId(), Instant.now()));
    }

    @Test
    void flywayAppliedEveryMigrationSuccessfully() {
        Integer failed = jdbc.queryForObject("select count(*) from flyway_schema_history where success = false", Integer.class);
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(failed).isZero();
        assertThat(applied).isGreaterThanOrEqualTo(1);
        for (String table : new String[]{"users", "missions", "simulation_runs", "telemetry_readings", "flight_events"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class)).isNotNull();
        }
    }

    @Test
    void duplicateEmailViolatesUniqueConstraint() {
        users.saveAndFlush(new User("A", "same@example.com", "h", Role.USER, Instant.now()));
        assertThatThrownBy(() -> users.saveAndFlush(new User("B", "same@example.com", "h", Role.USER, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeysRejectOrphansAndProtectAgainstAccidentalCascades() {
        SimulationRun run = newRun();
        assertThatThrownBy(() -> telemetry.saveAndFlush(new TelemetryReading(999_999L, 0, 1, 0, 1.0, 1.0, null, null, FlightState.READY, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> runs.saveAndFlush(new SimulationRun(999_999L, run.getUserId(), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        // a mission that still has runs cannot be deleted at the database level (ON DELETE RESTRICT)
        assertThatThrownBy(() -> jdbc.update("delete from missions where id = ?", run.getMissionId())).isInstanceOf(DataIntegrityViolationException.class);
        // a run that has telemetry cannot be deleted either
        telemetry.saveAndFlush(new TelemetryReading(run.getId(), 0, 1, 0, 1.0, 1.0, null, null, FlightState.READY, Instant.now()));
        assertThatThrownBy(() -> jdbc.update("delete from simulation_runs where id = ?", run.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidStatusIsRejectedByTheDatabase() {
        SimulationRun run = newRun();
        assertThatThrownBy(() -> jdbc.update("update simulation_runs set status = 'EXPLODED' where id = ?", run.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameEventCannotBeRecordedTwice() {
        SimulationRun run = newRun();
        Boolean first = tx.execute(s -> recorder.recordOnce(run.getId(), FlightEventType.PARACHUTE_DEPLOYED, "deployed", 2.0));
        Boolean second = tx.execute(s -> recorder.recordOnce(run.getId(), FlightEventType.PARACHUTE_DEPLOYED, "deployed again", 2.0));
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(events.countByRunId(run.getId())).isEqualTo(1);
        // and the unique constraint is the backstop if the existence check were bypassed
        assertThatThrownBy(() -> events.saveAndFlush(new FlightEvent(run.getId(), FlightEventType.PARACHUTE_DEPLOYED, "x", 2.0, Instant.now(), "PARACHUTE_DEPLOYED")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void conditionalTransitionsAllowExactlyOneWinner() {
        SimulationRun run = newRun();
        assertThat(runs.markStarted(run.getId(), run.getUserId(), Instant.now())).isEqualTo(1);
        assertThat(runs.markStarted(run.getId(), run.getUserId(), Instant.now())).isZero();
        assertThat(runs.markStarted(run.getId(), run.getUserId() + 1, Instant.now())).isZero();
        assertThat(runs.markCancelled(run.getId(), run.getUserId(), Instant.now(), "x")).isEqualTo(1);
        assertThat(runs.markFinished(run.getId(), SimulationStatus.COMPLETED, Instant.now(), 1.0, 1.0, "late", 1.0, 100.0)).isZero();
        assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(SimulationStatus.CANCELLED);
    }
}
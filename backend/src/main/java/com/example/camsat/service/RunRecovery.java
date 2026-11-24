package com.example.camsat.service;

import com.example.camsat.domain.FlightEventType;
import com.example.camsat.domain.SimulationRun;
import com.example.camsat.domain.SimulationStatus;
import com.example.camsat.repository.SimulationRunRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Simulation workers live in memory. If the process stopped while runs were RUNNING or PAUSED, no worker exists for
 * them after restart, so they are closed as FAILED instead of staying "running" forever. Assumes a single instance.
 * A failure here is logged but never stops the application from starting.
 */
@Component
public class RunRecovery {
    private static final Logger log = LoggerFactory.getLogger(RunRecovery.class);
    private final SimulationRunRepository runs;
    private final EventRecorder recorder;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RunRecovery(SimulationRunRepository runs, EventRecorder recorder, TransactionTemplate tx, Clock clock) {
        this.runs = runs; this.recorder = recorder; this.tx = tx; this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failOrphanedRuns() {
        try {
            List<SimulationRun> orphans = runs.findByStatusIn(SimulationStatus.ACTIVE);
            for (SimulationRun run : orphans) {
                tx.executeWithoutResult(s -> {
                    String reason = "Interrupted by a backend restart";
                    double elapsed = run.getElapsedSimSeconds() == null ? 0.0 : run.getElapsedSimSeconds();
                    if (runs.markFinished(run.getId(), SimulationStatus.FAILED, clock.instant(), null, null, reason, elapsed, null) == 1) {
                        recorder.recordOnce(run.getId(), FlightEventType.SIMULATION_FAILED, reason, elapsed);
                    }
                });
            }
            if (!orphans.isEmpty()) log.warn("Marked {} orphaned run(s) as FAILED after restart", orphans.size());
        } catch (RuntimeException e) {
            log.error("Orphaned-run recovery failed; continuing startup", e);
        }
    }
}
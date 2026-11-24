package com.example.camsat.simulation;

import com.example.camsat.config.AppProperties;
import com.example.camsat.domain.FlightEventType;
import com.example.camsat.domain.FlightState;
import com.example.camsat.domain.SimulationRun;
import com.example.camsat.domain.SimulationStatus;
import com.example.camsat.domain.TelemetryReading;
import com.example.camsat.repository.SimulationRunRepository;
import com.example.camsat.repository.TelemetryReadingRepository;
import com.example.camsat.service.EventRecorder;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs simulations on a bounded thread pool. No database transaction spans a whole simulation: every
 * telemetry batch is persisted in its own short transaction that first takes a row lock on the run
 * (only while it is RUNNING), so a concurrent cancel/pause/finish is strictly ordered with respect to it.
 * Execution state (pause/stop flags) lives in this JVM, so run exactly one backend instance.
 */
@Component
public class SimulationExecutor {
    private static final Logger log = LoggerFactory.getLogger(SimulationExecutor.class);

    private enum Persisted { OK, NOT_RUNNING }

    private final SimulationRunRepository runs;
    private final TelemetryReadingRepository telemetry;
    private final EventRecorder recorder;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final long sampleDelayMs;
    private final ThreadPoolExecutor pool;
    private final ConcurrentHashMap<Long, RunControl> controls = new ConcurrentHashMap<>();

    public SimulationExecutor(SimulationRunRepository runs, TelemetryReadingRepository telemetry, EventRecorder recorder,
                              TransactionTemplate tx, Clock clock, AppProperties props) {
        this.runs = runs; this.telemetry = telemetry; this.recorder = recorder; this.tx = tx; this.clock = clock;
        AppProperties.Simulation s = props.simulation();
        this.sampleDelayMs = Math.max(0, s.sampleDelayMs());
        AtomicInteger n = new AtomicInteger();
        this.pool = new ThreadPoolExecutor(Math.max(1, s.coreThreads()), Math.max(s.coreThreads(), s.maxThreads()),
                60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(Math.max(1, s.queueCapacity())),
                r -> { Thread t = new Thread(r, "simulation-" + n.incrementAndGet()); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** @throws RejectedExecutionException when the worker pool and its queue are full */
    public void submit(long runId, FlightSimulator.Params params) {
        RunControl control = new RunControl();
        if (controls.putIfAbsent(runId, control) != null) throw new IllegalStateException("Run " + runId + " is already executing");
        try {
            pool.execute(() -> execute(runId, params, control));
        } catch (RejectedExecutionException e) {
            controls.remove(runId);
            throw e;
        }
    }

    public void signalPause(long runId) { RunControl c = controls.get(runId); if (c != null) c.pause(); }
    public void signalResume(long runId) { RunControl c = controls.get(runId); if (c != null) c.resume(); }
    public void signalStop(long runId) { RunControl c = controls.get(runId); if (c != null) c.stop(); }
    public boolean isExecuting(long runId) { return controls.containsKey(runId); }

    @PreDestroy
    void shutdown() {
        controls.values().forEach(RunControl::stop);
        pool.shutdownNow();
    }

    private void execute(long runId, FlightSimulator.Params params, RunControl control) {
        try {
            FlightSimulator sim = new FlightSimulator(params);
            while (!sim.isFinished()) {
                if (!control.awaitRunnable()) return;
                FlightSimulator.Batch batch = sim.nextSamples();
                while (true) {
                    Persisted result = persist(runId, sim, batch);
                    if (result == Persisted.OK) break;
                    // Not RUNNING any more: cancelled/finished elsewhere (exit) or paused between check and write (wait, retry).
                    SimulationStatus now = runs.findById(runId).map(SimulationRun::getStatus).orElse(SimulationStatus.CANCELLED);
                    if (now != SimulationStatus.PAUSED || control.isStopped()) return;
                    Thread.sleep(10);
                    if (!control.awaitRunnable()) return;
                }
                if (sampleDelayMs > 0 && !sim.isFinished()) Thread.sleep(sampleDelayMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            log.error("Simulation of run {} failed with {}", runId, t.getClass().getName());
            log.debug("Simulation failure detail", t);
            failRun(runId, "Simulation failed because of an internal error");
        } finally {
            controls.remove(runId);
        }
    }

    private Persisted persist(long runId, FlightSimulator sim, FlightSimulator.Batch batch) {
        return tx.execute(status -> {
            Optional<SimulationRun> locked = runs.lockByIdAndStatus(runId, SimulationStatus.RUNNING);
            if (locked.isEmpty()) return Persisted.NOT_RUNNING;
            var now = clock.instant();
            List<TelemetryReading> rows = new ArrayList<>();
            for (FlightSimulator.Sample s : batch.samples()) {
                rows.add(new TelemetryReading(runId, s.elapsedS(), s.altitudeM(), s.verticalVelocityMps(), s.temperatureC(),
                        s.batteryPercent(), null, null, FlightState.valueOf(s.state().name()), now));
            }
            telemetry.saveAll(rows);
            for (FlightSimulator.Event e : batch.events()) {
                recorder.recordOnce(runId, FlightEventType.valueOf(e.kind().name()), e.message(), e.elapsedS());
            }
            if (sim.isFinished()) {
                boolean ok = sim.outcome() == FlightSimulator.Outcome.COMPLETED;
                Double landing = Double.isNaN(sim.landingTimeSeconds()) ? null : sim.landingTimeSeconds();
                Double drift = Double.isNaN(sim.landingDriftMeters()) ? null : sim.landingDriftMeters();
                String result = truncate(sim.result());
                int changed = runs.markFinished(runId, ok ? SimulationStatus.COMPLETED : SimulationStatus.FAILED, now,
                        landing, drift, result, sim.elapsedSeconds(), 100.0);
                if (changed == 1) {
                    recorder.recordOnce(runId, ok ? FlightEventType.SIMULATION_COMPLETED : FlightEventType.SIMULATION_FAILED,
                            result, sim.elapsedSeconds());
                }
            } else {
                runs.updateProgress(runId, sim.elapsedSeconds(), sim.progressPercent());
            }
            return Persisted.OK;
        });
    }

    private void failRun(long runId, String reason) {
        try {
            tx.executeWithoutResult(status -> {
                double elapsed = runs.findById(runId).map(SimulationRun::getElapsedSimSeconds).orElse(0.0);
                if (runs.markFinished(runId, SimulationStatus.FAILED, clock.instant(), null, null, reason, elapsed, null) == 1) {
                    recorder.recordOnce(runId, FlightEventType.SIMULATION_FAILED, reason, elapsed);
                }
            });
        } catch (RuntimeException e) {
            log.error("Could not record failure of run {} ({})", runId, e.getClass().getName());
        }
    }

    private static String truncate(String s) { return s == null ? null : s.length() > 500 ? s.substring(0, 500) : s; }
}

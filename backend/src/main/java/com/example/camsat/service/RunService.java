package com.example.camsat.service;

import com.example.camsat.domain.FlightEventType;
import com.example.camsat.domain.Mission;
import com.example.camsat.domain.SimulationRun;
import com.example.camsat.domain.SimulationStatus;
import com.example.camsat.dto.SimulationRunResponse;
import com.example.camsat.repository.FlightEventRepository;
import com.example.camsat.repository.MissionRepository;
import com.example.camsat.repository.SimulationRunRepository;
import com.example.camsat.simulation.FlightSimulator;
import com.example.camsat.simulation.SimulationExecutor;
import com.example.camsat.web.ApiException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Run lifecycle:  CREATED -> RUNNING <-> PAUSED -> COMPLETED | FAILED,  and CREATED/RUNNING/PAUSED -> CANCELLED.
 * Every transition is a conditional UPDATE, so of two concurrent requests exactly one succeeds and the other gets 409.
 */
@Service
public class RunService {
    private static final int RECENT_LIMIT = 10;

    private final SimulationRunRepository runs;
    private final MissionRepository missions;
    private final FlightEventRepository events;
    private final EventRecorder recorder;
    private final RunAccess access;
    private final SimulationExecutor executor;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RunService(SimulationRunRepository runs, MissionRepository missions, FlightEventRepository events, EventRecorder recorder,
                      RunAccess access, SimulationExecutor executor, TransactionTemplate tx, Clock clock) {
        this.runs = runs; this.missions = missions; this.events = events; this.recorder = recorder;
        this.access = access; this.executor = executor; this.tx = tx; this.clock = clock;
    }

    @Transactional
    public SimulationRunResponse create(long userId, long missionId) {
        missions.lockByIdAndUserId(missionId, userId).orElseThrow(() -> ApiException.notFound("Mission " + missionId + " was not found."));
        return SimulationRunResponse.from(runs.save(new SimulationRun(missionId, userId, clock.instant())));
    }

    @Transactional(readOnly = true)
    public List<SimulationRunResponse> byMission(long userId, long missionId) {
        missions.findByIdAndUserId(missionId, userId).orElseThrow(() -> ApiException.notFound("Mission " + missionId + " was not found."));
        return runs.findByMissionIdAndUserIdOrderByIdDesc(missionId, userId).stream().map(SimulationRunResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SimulationRunResponse get(long userId, long runId) { return SimulationRunResponse.from(access.require(userId, runId)); }

    @Transactional(readOnly = true)
    public List<SimulationRunResponse> recent(long userId) {
        return runs.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, RECENT_LIMIT)).stream().map(SimulationRunResponse::from).toList();
    }

    /** Starts asynchronously: returns once the run is persisted as RUNNING; the worker continues in the background. */
    public SimulationRunResponse start(long userId, long runId) {
        SimulationRun run = access.require(userId, runId);
        Mission mission = missions.findByIdAndUserId(run.getMissionId(), userId)
                .orElseThrow(() -> ApiException.notFound("Mission " + run.getMissionId() + " was not found."));
        FlightSimulator.Params params = new FlightSimulator.Params(mission.getPayloadMassKg(), mission.getReleaseAltitudeM(),
                mission.getParachuteDiameterM(), mission.getDragCoefficient(), mission.getWindSpeedMps(),
                mission.getWindDirectionDeg(), mission.getInitialBatteryPercent());
        try {
            new FlightSimulator(params); // rejects non-finite / non-physical parameters before anything is persisted
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("The mission parameters cannot be simulated: " + e.getMessage() + ".");
        }

        Boolean started = tx.execute(s -> {
            if (runs.markStarted(runId, userId, clock.instant()) == 0) return false;
            recorder.recordOnce(runId, FlightEventType.SIMULATION_STARTED, "Simulation started", 0.0);
            return true;
        });
        if (!Boolean.TRUE.equals(started)) {
            throw ApiException.conflict("Run " + runId + " cannot be started from status " + access.require(userId, runId).getStatus() + ".");
        }
        try {
            executor.submit(runId, params);
        } catch (RejectedExecutionException e) {
            tx.executeWithoutResult(s -> {
                runs.revertStart(runId);
                events.deleteByRunIdAndDedupeKey(runId, FlightEventType.SIMULATION_STARTED.name());
            });
            throw ApiException.busy("The simulator is at capacity. Try again shortly.");
        }
        return get(userId, runId);
    }

    public SimulationRunResponse pause(long userId, long runId) {
        access.require(userId, runId);
        Boolean ok = tx.execute(s -> {
            if (runs.markPaused(runId, userId) == 0) return false;
            recordRepeatable(runId, FlightEventType.SIMULATION_PAUSED, "Simulation paused");
            return true;
        });
        if (!Boolean.TRUE.equals(ok)) throw conflictFor(userId, runId, "paused", "RUNNING");
        executor.signalPause(runId);
        return get(userId, runId);
    }

    public SimulationRunResponse resume(long userId, long runId) {
        access.require(userId, runId);
        Boolean ok = tx.execute(s -> {
            if (runs.markResumed(runId, userId) == 0) return false;
            recordRepeatable(runId, FlightEventType.SIMULATION_RESUMED, "Simulation resumed");
            return true;
        });
        if (!Boolean.TRUE.equals(ok)) throw conflictFor(userId, runId, "resumed", "PAUSED");
        executor.signalResume(runId);
        return get(userId, runId);
    }

    public SimulationRunResponse cancel(long userId, long runId) {
        access.require(userId, runId);
        Boolean ok = tx.execute(s -> {
            if (runs.markCancelled(runId, userId, clock.instant(), "Cancelled by user") == 0) return false;
            double elapsed = runs.findById(runId).map(SimulationRun::getElapsedSimSeconds).orElse(0.0);
            recorder.recordOnce(runId, FlightEventType.SIMULATION_CANCELLED, "Simulation cancelled by user", elapsed);
            return true;
        });
        if (!Boolean.TRUE.equals(ok)) {
            throw ApiException.conflict("Run " + runId + " cannot be cancelled from status " + access.require(userId, runId).getStatus() + ".");
        }
        executor.signalStop(runId); // cooperative: the worker exits at its next check; no telemetry is written after the transition
        return get(userId, runId);
    }

    private void recordRepeatable(long runId, FlightEventType type, String message) {
        double elapsed = runs.findById(runId).map(SimulationRun::getElapsedSimSeconds).orElse(0.0);
        long n = events.countByRunIdAndEventType(runId, type) + 1;
        recorder.record(runId, type, message, elapsed, type.name() + ":" + n);
    }

    private ApiException conflictFor(long userId, long runId, String verb, String required) {
        SimulationStatus current = access.require(userId, runId).getStatus();
        return ApiException.conflict("Run " + runId + " cannot be " + verb + " from status " + current + " (requires " + required + ").");
    }
}

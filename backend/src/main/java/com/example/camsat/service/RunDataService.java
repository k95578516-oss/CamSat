package com.example.camsat.service;

import com.example.camsat.config.AppProperties;
import com.example.camsat.domain.FlightEvent;
import com.example.camsat.domain.FlightEventType;
import com.example.camsat.domain.Mission;
import com.example.camsat.domain.SimulationRun;
import com.example.camsat.domain.SimulationStatus;
import com.example.camsat.dto.FlightEventResponse;
import com.example.camsat.dto.MissionReportEvent;
import com.example.camsat.dto.MissionReportResponse;
import com.example.camsat.dto.RecoveryResponse;
import com.example.camsat.dto.TelemetryReadingResponse;
import com.example.camsat.dto.TelemetryStats;
import com.example.camsat.repository.FlightEventRepository;
import com.example.camsat.repository.MissionRepository;
import com.example.camsat.repository.TelemetryReadingRepository;
import com.example.camsat.web.ApiException;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only views of one run's telemetry, events, recovery state and report. Every call re-checks ownership. */
@Service
@Transactional(readOnly = true)
public class RunDataService {
    private final RunAccess access;
    private final MissionRepository missions;
    private final TelemetryReadingRepository telemetry;
    private final FlightEventRepository events;
    private final int maxRows;

    public RunDataService(RunAccess access, MissionRepository missions, TelemetryReadingRepository telemetry,
                          FlightEventRepository events, AppProperties props) {
        this.access = access; this.missions = missions; this.telemetry = telemetry; this.events = events;
        this.maxRows = props.telemetry() != null && props.telemetry().maxRows() > 0 ? props.telemetry().maxRows() : 20000;
    }

    // ---------------- telemetry ----------------

    /** Optional window [fromSeconds, toSeconds] and limit; without parameters the whole series (capped at max-rows) is returned. */
    public List<TelemetryReadingResponse> telemetry(long userId, long runId, Double from, Double to, Integer limit) {
        access.require(userId, runId);
        if (from != null && to != null && from > to) throw ApiException.badRequest("fromSeconds must not exceed toSeconds.");
        if (limit != null && limit < 1) throw ApiException.badRequest("limit must be at least 1.");
        int size = limit == null ? maxRows : Math.min(limit, maxRows);
        return telemetry.window(runId, from == null ? -1.0 : from, to == null ? Double.MAX_VALUE : to, PageRequest.of(0, size))
                .stream().map(TelemetryReadingResponse::from).toList();
    }

    public TelemetryReadingResponse latestReading(long userId, long runId) {
        access.require(userId, runId);
        return telemetry.findFirstByRunIdOrderByElapsedTimeSecondsDescIdDesc(runId).map(TelemetryReadingResponse::from)
                .orElseThrow(() -> ApiException.notFound("No telemetry has been recorded for run " + runId + " yet."));
    }

    // ---------------- events ----------------

    public List<FlightEventResponse> events(long userId, long runId) {
        access.require(userId, runId);
        return events.findByRunIdOrderByElapsedTimeSecondsAscIdAsc(runId).stream().map(FlightEventResponse::from).toList();
    }

    /** The five most recent events, newest first. */
    public List<FlightEventResponse> latestEvents(long userId, long runId) {
        access.require(userId, runId);
        return events.findTop5ByRunIdOrderByElapsedTimeSecondsDescIdDesc(runId).stream().map(FlightEventResponse::from).toList();
    }

    public long eventCount(long userId, long runId) {
        access.require(userId, runId);
        return events.countByRunId(runId);
    }

    // ---------------- recovery (derived only from persisted run state and events) ----------------

    public RecoveryResponse recovery(long userId, long runId) {
        SimulationRun run = access.require(userId, runId);
        Mission mission = mission(run);
        boolean landed = events.existsByRunIdAndEventType(runId, FlightEventType.LANDING_DETECTED);
        boolean started = events.existsByRunIdAndEventType(runId, FlightEventType.RECOVERY_STARTED);
        boolean completed = events.existsByRunIdAndEventType(runId, FlightEventType.RECOVERY_COMPLETED);
        List<FlightEvent> latest = events.findTop5ByRunIdOrderByElapsedTimeSecondsDescIdDesc(runId);
        Instant lastEventAt = latest.stream().map(FlightEvent::getCreatedAt).max(Instant::compareTo).orElse(null);

        SimulationStatus st = run.getStatus();
        String status;
        String message;
        if (completed) {
            status = "COMPLETED"; message = "Payload recovered.";
        } else if (st == SimulationStatus.FAILED) {
            status = "FAILED"; message = landed ? "The run failed after touchdown; recovery did not complete." : "The run failed before touchdown; the payload was not recovered.";
        } else if (st == SimulationStatus.CANCELLED) {
            status = "NOT_STARTED"; message = "The run was cancelled before recovery completed.";
        } else if (started) {
            status = "IN_PROGRESS"; message = "Recovery team is on its way to the payload.";
        } else if (landed) {
            status = "NOT_STARTED"; message = "Touchdown detected; recovery has not started yet.";
        } else {
            status = "PENDING"; message = st == SimulationStatus.CREATED ? "The run has not started." : "The payload has not landed yet.";
        }
        return new RecoveryResponse(runId, run.getMissionId(), mission.getName(), st, status, landed, started, completed,
                run.getLandingDriftM(), message, lastEventAt);
    }

    // ---------------- report ----------------

    public MissionReportResponse report(long userId, long runId) {
        SimulationRun run = access.require(userId, runId);
        Mission mission = mission(run);
        TelemetryStats stats = telemetry.stats(runId);
        var last = telemetry.findFirstByRunIdOrderByElapsedTimeSecondsDescIdDesc(runId);
        List<MissionReportEvent> evs = events.findByRunIdOrderByElapsedTimeSecondsAscIdAsc(runId).stream()
                .map(e -> new MissionReportEvent(e.getId(), e.getEventType(), e.getMessage(), e.getElapsedTimeSeconds(), e.getCreatedAt()))
                .toList();
        long count = stats == null || stats.count() == null ? 0 : stats.count();
        return new MissionReportResponse(runId, run.getMissionId(), mission.getName(), run.getStatus(), run.getStartedAt(), run.getEndedAt(),
                run.getLandingTimeSeconds(), run.getLandingDriftM(), run.getFinalResult(),
                mission.getReleaseAltitudeM(), mission.getPayloadMassKg(), mission.getParachuteDiameterM(), mission.getWindSpeedMps(),
                count,
                count == 0 ? null : stats.minBattery(),
                count == 0 ? null : stats.maxAltitude(),
                count == 0 ? null : stats.maxDescentSpeed(),
                last.map(t -> t.getFlightState()).orElse(null),
                evs);
    }

    private Mission mission(SimulationRun run) {
        return missions.findByIdAndUserId(run.getMissionId(), run.getUserId())
                .orElseThrow(() -> ApiException.notFound("Mission " + run.getMissionId() + " was not found."));
    }
}

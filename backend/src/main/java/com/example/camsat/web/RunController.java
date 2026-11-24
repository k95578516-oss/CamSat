package com.example.camsat.web;

import com.example.camsat.dto.*;
import com.example.camsat.security.AuthUser;
import com.example.camsat.service.RunDataService;
import com.example.camsat.service.RunService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/runs")
public class RunController {
    private final RunService runs;
    private final RunDataService data;
    public RunController(RunService runs, RunDataService data) { this.runs = runs; this.data = data; }

    @GetMapping("/recent")
    public List<SimulationRunResponse> recent(@AuthenticationPrincipal AuthUser u) { return runs.recent(u.id()); }

    @GetMapping("/{id}")
    public SimulationRunResponse get(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { return runs.get(u.id(), id); }

    /** 202 Accepted: the run is RUNNING and continues in the background; poll GET /api/runs/{id}. */
    @PostMapping("/{id}/start") @ResponseStatus(HttpStatus.ACCEPTED)
    public SimulationRunResponse start(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { return runs.start(u.id(), id); }

    @PostMapping("/{id}/pause")
    public SimulationRunResponse pause(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { return runs.pause(u.id(), id); }

    @PostMapping("/{id}/resume")
    public SimulationRunResponse resume(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { return runs.resume(u.id(), id); }

    @PostMapping("/{id}/cancel")
    public SimulationRunResponse cancel(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { return runs.cancel(u.id(), id); }

    @GetMapping("/{id}/telemetry")
    public List<TelemetryReadingResponse> telemetry(@AuthenticationPrincipal AuthUser u, @PathVariable long id,
            @RequestParam(required = false) Double fromSeconds, @RequestParam(required = false) Double toSeconds,
            @RequestParam(required = false) Integer limit) {
        return data.telemetry(u.id(), id, fromSeconds, toSeconds, limit);
    }

    @GetMapping("/{id}/telemetry/latest-reading")
    public TelemetryReadingResponse latestReading(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { return data.latestReading(u.id(), id); }

    @GetMapping("/{id}/events")
    public List<FlightEventResponse> events(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { return data.events(u.id(), id); }

    @GetMapping("/{id}/events/latest")
    public List<FlightEventResponse> latestEvents(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { return data.latestEvents(u.id(), id); }

    @GetMapping("/{id}/events/count")
    public long eventCount(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { return data.eventCount(u.id(), id); }

    @GetMapping("/{id}/recovery")
    public RecoveryResponse recovery(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { return data.recovery(u.id(), id); }

    @GetMapping("/{id}/report")
    public MissionReportResponse report(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { return data.report(u.id(), id); }
}

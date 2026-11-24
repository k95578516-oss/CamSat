package com.example.camsat.web;

import com.example.camsat.dto.MissionRequest;
import com.example.camsat.dto.MissionResponse;
import com.example.camsat.dto.SimulationRunResponse;
import com.example.camsat.security.AuthUser;
import com.example.camsat.service.MissionService;
import com.example.camsat.service.RunService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/missions")
public class MissionController {
    private final MissionService missions;
    private final RunService runs;
    public MissionController(MissionService missions, RunService runs) { this.missions = missions; this.runs = runs; }

    @GetMapping
    public List<MissionResponse> list(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String search) {
        return missions.list(u.id(), search);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MissionResponse create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody MissionRequest req) { return missions.create(u.id(), req); }

    @GetMapping("/{id}")
    public MissionResponse get(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { return missions.get(u.id(), id); }

    @PutMapping("/{id}")
    public MissionResponse update(@AuthenticationPrincipal AuthUser u, @PathVariable long id, @Valid @RequestBody MissionRequest req) {
        return missions.update(u.id(), id, req);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser u, @PathVariable long id) { missions.delete(u.id(), id); }

    @PostMapping("/{missionId}/runs") @ResponseStatus(HttpStatus.CREATED)
    public SimulationRunResponse createRun(@AuthenticationPrincipal AuthUser u, @PathVariable long missionId) { return runs.create(u.id(), missionId); }

    @GetMapping("/{missionId}/runs")
    public List<SimulationRunResponse> runsOf(@AuthenticationPrincipal AuthUser u, @PathVariable long missionId) { return runs.byMission(u.id(), missionId); }
}

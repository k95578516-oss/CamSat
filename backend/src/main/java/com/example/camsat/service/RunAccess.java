package com.example.camsat.service;

import com.example.camsat.domain.SimulationRun;
import com.example.camsat.repository.SimulationRunRepository;
import com.example.camsat.web.ApiException;
import org.springframework.stereotype.Component;

/** The single place where run ownership is enforced. A run owned by someone else is reported as not found. */
@Component
public class RunAccess {
    private final SimulationRunRepository runs;
    public RunAccess(SimulationRunRepository runs) { this.runs = runs; }

    public SimulationRun require(long userId, long runId) {
        return runs.findByIdAndUserId(runId, userId).orElseThrow(() -> ApiException.notFound("Run " + runId + " was not found."));
    }
}

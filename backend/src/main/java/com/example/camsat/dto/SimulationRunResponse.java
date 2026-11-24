package com.example.camsat.dto;

import com.example.camsat.domain.SimulationRun;
import com.example.camsat.domain.SimulationStatus;
import java.time.Instant;

/** The last two fields are additive (optional in the TypeScript type); existing consumers ignore them. */
public record SimulationRunResponse(long id, long missionId, SimulationStatus status, Instant startedAt, Instant endedAt,
                                    Double landingTimeSeconds, Double landingDriftM, String finalResult,
                                    Double elapsedSimTimeSeconds, Double progressPercent) {
    public static SimulationRunResponse from(SimulationRun r) {
        return new SimulationRunResponse(r.getId(), r.getMissionId(), r.getStatus(), r.getStartedAt(), r.getEndedAt(),
                r.getLandingTimeSeconds(), r.getLandingDriftM(), r.getFinalResult(),
                r.getElapsedSimSeconds(), r.getProgressPercent());
    }
}

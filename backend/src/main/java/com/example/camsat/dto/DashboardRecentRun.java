package com.example.camsat.dto;

import com.example.camsat.domain.SimulationStatus;
import java.time.Instant;

public record DashboardRecentRun(Long runId, Long missionId, String missionName, SimulationStatus status, Instant startedAt,
                                 Instant endedAt, Double landingTimeSeconds, Double landingDriftM, String finalResult) { }

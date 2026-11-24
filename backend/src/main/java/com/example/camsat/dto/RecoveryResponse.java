package com.example.camsat.dto;

import com.example.camsat.domain.SimulationStatus;
import java.time.Instant;

public record RecoveryResponse(long runId, long missionId, String missionName, SimulationStatus simulationStatus,
                               String recoveryStatus, boolean landingDetected, boolean recoveryStarted,
                               boolean recoveryCompleted, Double landingDriftM, String message, Instant lastEventAt) { }

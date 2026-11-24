package com.example.camsat.dto;

import com.example.camsat.domain.SimulationStatus;
import java.util.List;
import java.util.Map;

public record DashboardResponse(long totalMissions, long totalSimulationRuns, long completedRuns, long failedRuns,
                                long activeRuns, long createdRuns, long totalTelemetryReadings,
                                Map<SimulationStatus, Long> runsByStatus, List<DashboardRecentRun> recentRuns) { }

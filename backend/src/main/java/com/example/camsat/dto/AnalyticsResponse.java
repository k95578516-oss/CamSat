package com.example.camsat.dto;

public record AnalyticsResponse(long totalRuns, long completedRuns, long failedRuns, double successRatePercent,
                                Double averageLandingTimeSeconds, Double averageLandingDriftMeters,
                                Double averageMaximumDescentSpeedMps) { }

package com.example.camsat.dto;

import com.example.camsat.domain.FlightState;
import com.example.camsat.domain.SimulationStatus;
import java.time.Instant;
import java.util.List;

public record MissionReportResponse(long runId, long missionId, String missionName, SimulationStatus status, Instant startedAt,
                                    Instant endedAt, Double landingTimeSeconds, Double landingDriftM, String finalResult,
                                    double releaseAltitudeM, double payloadMassKg, double parachuteDiameterM, double windSpeedMps,
                                    long telemetryReadingCount, Double minimumBatteryPercent, Double maximumAltitudeMeters,
                                    Double maximumDescentSpeedMps, FlightState finalFlightState, List<MissionReportEvent> events) { }

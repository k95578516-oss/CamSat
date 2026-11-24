package com.example.camsat.dto;

import com.example.camsat.domain.FlightEventType;
import java.time.Instant;

public record MissionReportEvent(long id, FlightEventType eventType, String message, double elapsedTimeSeconds, Instant createdAt) { }

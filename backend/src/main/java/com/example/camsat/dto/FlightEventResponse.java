package com.example.camsat.dto;

import com.example.camsat.domain.FlightEvent;
import com.example.camsat.domain.FlightEventType;
import java.time.Instant;

public record FlightEventResponse(long id, long runId, FlightEventType eventType, String message,
                                  double elapsedTimeSeconds, Instant createdAt) {
    public static FlightEventResponse from(FlightEvent e) {
        return new FlightEventResponse(e.getId(), e.getRunId(), e.getEventType(), e.getMessage(),
                e.getElapsedTimeSeconds(), e.getCreatedAt());
    }
}

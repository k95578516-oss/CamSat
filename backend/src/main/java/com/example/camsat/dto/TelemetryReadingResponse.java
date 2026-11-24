package com.example.camsat.dto;

import com.example.camsat.domain.FlightState;
import com.example.camsat.domain.TelemetryReading;
import java.time.Instant;

public record TelemetryReadingResponse(long id, long runId, double elapsedTimeSeconds, double altitudeMeters,
                                       double verticalVelocityMps, Double temperatureCelsius, Double batteryPercent,
                                       Double latitude, Double longitude, FlightState flightState, Instant recordedAt) {
    public static TelemetryReadingResponse from(TelemetryReading t) {
        return new TelemetryReadingResponse(t.getId(), t.getRunId(), t.getElapsedTimeSeconds(), t.getAltitudeMeters(),
                t.getVerticalVelocityMps(), t.getTemperatureCelsius(), t.getBatteryPercent(), t.getLatitude(),
                t.getLongitude(), t.getFlightState(), t.getRecordedAt());
    }
}

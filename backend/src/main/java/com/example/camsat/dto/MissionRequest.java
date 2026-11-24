package com.example.camsat.dto;

import jakarta.validation.constraints.*;

/**
 * Limits (see backend/docs/SIMULATION_MODEL.md, "Validation limits"):
 * mass 0.05-50 kg, release altitude 20-10000 m, canopy 0.1-3 m, Cd 0.1-2.0, wind 0-50 m/s,
 * direction [0,360) degrees, battery 1-100 %.
 */
public record MissionRequest(
        @NotBlank(message = "Mission name is required") @Size(max = 100, message = "Mission name cannot exceed 100 characters") String name,
        @NotNull(message = "Payload mass is required") @DecimalMin(value = "0.05", message = "Payload mass must be at least 0.05 kg") @DecimalMax(value = "50", message = "Payload mass cannot exceed 50 kg") Double payloadMassKg,
        @NotNull(message = "Release altitude is required") @DecimalMin(value = "20", message = "Release altitude must be at least 20 m") @DecimalMax(value = "10000", message = "Release altitude cannot exceed 10000 m") Double releaseAltitudeM,
        @NotNull(message = "Parachute diameter is required") @DecimalMin(value = "0.1", message = "Parachute diameter must be at least 0.1 m") @DecimalMax(value = "3", message = "Parachute diameter cannot exceed 3 m") Double parachuteDiameterM,
        @NotNull(message = "Drag coefficient is required") @DecimalMin(value = "0.1", message = "Drag coefficient must be at least 0.1") @DecimalMax(value = "2", message = "Drag coefficient cannot exceed 2.0") Double dragCoefficient,
        @NotNull(message = "Wind speed is required") @DecimalMin(value = "0", message = "Wind speed cannot be negative") @DecimalMax(value = "50", message = "Wind speed cannot exceed 50 m/s") Double windSpeedMps,
        @NotNull(message = "Wind direction is required") @DecimalMin(value = "0", message = "Wind direction must be at least 0") @DecimalMax(value = "360", inclusive = false, message = "Wind direction must be below 360") Double windDirectionDeg,
        @NotNull(message = "Initial battery is required") @DecimalMin(value = "1", message = "Initial battery must be at least 1 %") @DecimalMax(value = "100", message = "Initial battery cannot exceed 100 %") Double initialBatteryPercent) { }

package com.example.camsat.dto;

import com.example.camsat.domain.Mission;
import java.time.Instant;

public record MissionResponse(long id, String name, double payloadMassKg, double releaseAltitudeM, double parachuteDiameterM,
                              double dragCoefficient, double windSpeedMps, double windDirectionDeg,
                              double initialBatteryPercent, Instant createdAt, Instant updatedAt) {
    public static MissionResponse from(Mission m) {
        return new MissionResponse(m.getId(), m.getName(), m.getPayloadMassKg(), m.getReleaseAltitudeM(), m.getParachuteDiameterM(),
                m.getDragCoefficient(), m.getWindSpeedMps(), m.getWindDirectionDeg(), m.getInitialBatteryPercent(),
                m.getCreatedAt(), m.getUpdatedAt());
    }
}

package com.example.camsat.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "missions")
public class Mission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "payload_mass_kg", nullable = false)
    private double payloadMassKg;
    @Column(name = "release_altitude_m", nullable = false)
    private double releaseAltitudeM;
    @Column(name = "parachute_diameter_m", nullable = false)
    private double parachuteDiameterM;
    @Column(name = "drag_coefficient", nullable = false)
    private double dragCoefficient;
    @Column(name = "wind_speed_mps", nullable = false)
    private double windSpeedMps;
    @Column(name = "wind_direction_deg", nullable = false)
    private double windDirectionDeg;
    @Column(name = "initial_battery_percent", nullable = false)
    private double initialBatteryPercent;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Mission() { }
    public Mission(Long userId, Instant now) { this.userId = userId; this.createdAt = now; this.updatedAt = now; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public double getPayloadMassKg() { return payloadMassKg; }
    public void setPayloadMassKg(double v) { this.payloadMassKg = v; }
    public double getReleaseAltitudeM() { return releaseAltitudeM; }
    public void setReleaseAltitudeM(double v) { this.releaseAltitudeM = v; }
    public double getParachuteDiameterM() { return parachuteDiameterM; }
    public void setParachuteDiameterM(double v) { this.parachuteDiameterM = v; }
    public double getDragCoefficient() { return dragCoefficient; }
    public void setDragCoefficient(double v) { this.dragCoefficient = v; }
    public double getWindSpeedMps() { return windSpeedMps; }
    public void setWindSpeedMps(double v) { this.windSpeedMps = v; }
    public double getWindDirectionDeg() { return windDirectionDeg; }
    public void setWindDirectionDeg(double v) { this.windDirectionDeg = v; }
    public double getInitialBatteryPercent() { return initialBatteryPercent; }
    public void setInitialBatteryPercent(double v) { this.initialBatteryPercent = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}

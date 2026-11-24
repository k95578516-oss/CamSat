package com.example.camsat.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "telemetry_readings")
public class TelemetryReading {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "run_id", nullable = false, updatable = false) private Long runId;
    @Column(name = "elapsed_time_seconds", nullable = false) private double elapsedTimeSeconds;
    @Column(name = "altitude_meters", nullable = false) private double altitudeMeters;
    @Column(name = "vertical_velocity_mps", nullable = false) private double verticalVelocityMps;
    @Column(name = "temperature_celsius") private Double temperatureCelsius;
    @Column(name = "battery_percent") private Double batteryPercent;
    @Column(name = "latitude") private Double latitude;
    @Column(name = "longitude") private Double longitude;
    @Enumerated(EnumType.STRING) @Column(name = "flight_state", nullable = false, length = 30) private FlightState flightState;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;

    protected TelemetryReading() { }
    public TelemetryReading(Long runId, double elapsed, double alt, double vel, Double temp, Double battery,
                            Double lat, Double lon, FlightState state, Instant recordedAt) {
        this.runId = runId; this.elapsedTimeSeconds = elapsed; this.altitudeMeters = alt; this.verticalVelocityMps = vel;
        this.temperatureCelsius = temp; this.batteryPercent = battery; this.latitude = lat; this.longitude = lon;
        this.flightState = state; this.recordedAt = recordedAt;
    }
    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public double getElapsedTimeSeconds() { return elapsedTimeSeconds; }
    public double getAltitudeMeters() { return altitudeMeters; }
    public double getVerticalVelocityMps() { return verticalVelocityMps; }
    public Double getTemperatureCelsius() { return temperatureCelsius; }
    public Double getBatteryPercent() { return batteryPercent; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public FlightState getFlightState() { return flightState; }
    public Instant getRecordedAt() { return recordedAt; }
}

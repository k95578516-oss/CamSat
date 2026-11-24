package com.example.camsat.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "flight_events")
public class FlightEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "run_id", nullable = false, updatable = false) private Long runId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 40) private FlightEventType eventType;
    @Column(nullable = false, length = 500) private String message;
    @Column(name = "elapsed_time_seconds", nullable = false) private double elapsedTimeSeconds;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "dedupe_key", nullable = false, length = 80, updatable = false) private String dedupeKey;

    protected FlightEvent() { }
    public FlightEvent(Long runId, FlightEventType type, String message, double elapsed, Instant createdAt, String dedupeKey) {
        this.runId = runId; this.eventType = type;
        this.message = message.length() > 500 ? message.substring(0, 500) : message;
        this.elapsedTimeSeconds = elapsed; this.createdAt = createdAt; this.dedupeKey = dedupeKey;
    }
    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public FlightEventType getEventType() { return eventType; }
    public String getMessage() { return message; }
    public double getElapsedTimeSeconds() { return elapsedTimeSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public String getDedupeKey() { return dedupeKey; }
}

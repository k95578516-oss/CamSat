package com.example.camsat.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A run is inserted once (status CREATED). Every later state change goes through the conditional
 * UPDATE statements in SimulationRunRepository, never through save(), so concurrent transitions
 * cannot overwrite each other.
 */
@Entity
@Table(name = "simulation_runs")
public class SimulationRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "mission_id", nullable = false, updatable = false)
    private Long missionId;
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private SimulationStatus status;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Column(name = "landing_time_seconds") private Double landingTimeSeconds;
    @Column(name = "landing_drift_m") private Double landingDriftM;
    @Column(name = "final_result", length = 500) private String finalResult;
    @Column(name = "elapsed_sim_seconds") private Double elapsedSimSeconds;
    @Column(name = "progress_percent") private Double progressPercent;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected SimulationRun() { }
    public SimulationRun(Long missionId, Long userId, Instant now) {
        this.missionId = missionId; this.userId = userId; this.status = SimulationStatus.CREATED; this.createdAt = now;
    }
    public Long getId() { return id; }
    public Long getMissionId() { return missionId; }
    public Long getUserId() { return userId; }
    public SimulationStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Double getLandingTimeSeconds() { return landingTimeSeconds; }
    public Double getLandingDriftM() { return landingDriftM; }
    public String getFinalResult() { return finalResult; }
    public Double getElapsedSimSeconds() { return elapsedSimSeconds; }
    public Double getProgressPercent() { return progressPercent; }
    public Instant getCreatedAt() { return createdAt; }
}

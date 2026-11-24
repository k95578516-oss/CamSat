package com.example.camsat.service;

import com.example.camsat.domain.SimulationStatus;
import com.example.camsat.dto.AnalyticsResponse;
import com.example.camsat.dto.DashboardResponse;
import com.example.camsat.repository.MissionRepository;
import com.example.camsat.repository.SimulationRunRepository;
import com.example.camsat.repository.TelemetryReadingRepository;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Definitions:  active = RUNNING + PAUSED;  successRatePercent = COMPLETED / (COMPLETED + FAILED) * 100 (cancelled and
 * unfinished runs are excluded; 0 when no run has finished);  averages use COMPLETED runs only and are null when none exist.
 * All figures are computed by the database and scoped to the authenticated user.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {
    private static final int RECENT = 5;
    private final MissionRepository missions;
    private final SimulationRunRepository runs;
    private final TelemetryReadingRepository telemetry;

    public DashboardService(MissionRepository missions, SimulationRunRepository runs, TelemetryReadingRepository telemetry) {
        this.missions = missions; this.runs = runs; this.telemetry = telemetry;
    }

    private Map<SimulationStatus, Long> counts(long userId) {
        Map<SimulationStatus, Long> m = new EnumMap<>(SimulationStatus.class);
        for (Object[] row : runs.countByStatus(userId)) m.put((SimulationStatus) row[0], ((Number) row[1]).longValue());
        return m;
    }

    public DashboardResponse dashboard(long userId) {
        Map<SimulationStatus, Long> c = counts(userId);
        long total = c.values().stream().mapToLong(Long::longValue).sum();
        long active = c.getOrDefault(SimulationStatus.RUNNING, 0L) + c.getOrDefault(SimulationStatus.PAUSED, 0L);
        return new DashboardResponse(missions.countByUserId(userId), total, c.getOrDefault(SimulationStatus.COMPLETED, 0L),
                c.getOrDefault(SimulationStatus.FAILED, 0L), active, c.getOrDefault(SimulationStatus.CREATED, 0L),
                telemetry.countForUser(userId), c, runs.recent(userId, PageRequest.of(0, RECENT)));
    }

    public AnalyticsResponse analytics(long userId) {
        Map<SimulationStatus, Long> c = counts(userId);
        long total = c.values().stream().mapToLong(Long::longValue).sum();
        long done = c.getOrDefault(SimulationStatus.COMPLETED, 0L);
        long failed = c.getOrDefault(SimulationStatus.FAILED, 0L);
        double rate = done + failed == 0 ? 0.0 : 100.0 * done / (done + failed);
        return new AnalyticsResponse(total, done, failed, rate, runs.averageLandingTime(userId), runs.averageLandingDrift(userId),
                runs.averageMaxDescentSpeed(userId));
    }
}

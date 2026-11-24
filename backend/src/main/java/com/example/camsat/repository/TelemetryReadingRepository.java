package com.example.camsat.repository;

import com.example.camsat.domain.TelemetryReading;
import com.example.camsat.dto.TelemetryStats;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, Long> {

    @Query("select t from TelemetryReading t where t.runId = :runId and t.elapsedTimeSeconds >= :from and t.elapsedTimeSeconds <= :to "
            + "order by t.elapsedTimeSeconds asc, t.id asc")
    List<TelemetryReading> window(@Param("runId") Long runId, @Param("from") double from, @Param("to") double to, Pageable page);

    Optional<TelemetryReading> findFirstByRunIdOrderByElapsedTimeSecondsDescIdDesc(Long runId);

    long countByRunId(Long runId);

    @Query("select new com.example.camsat.dto.TelemetryStats(count(t), max(t.altitudeMeters), min(t.batteryPercent), max(0 - t.verticalVelocityMps)) "
            + "from TelemetryReading t where t.runId = :runId")
    TelemetryStats stats(@Param("runId") Long runId);

    @Query("select count(t) from TelemetryReading t where t.runId in (select r.id from SimulationRun r where r.userId = :userId)")
    long countForUser(@Param("userId") Long userId);
}

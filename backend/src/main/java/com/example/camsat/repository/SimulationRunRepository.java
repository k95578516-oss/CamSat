package com.example.camsat.repository;

import com.example.camsat.domain.SimulationRun;
import com.example.camsat.domain.SimulationStatus;
import com.example.camsat.dto.DashboardRecentRun;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, Long> {

    Optional<SimulationRun> findByIdAndUserId(Long id, Long userId);

    List<SimulationRun> findByMissionIdAndUserIdOrderByIdDesc(Long missionId, Long userId);

    List<SimulationRun> findByUserIdOrderByIdDesc(Long userId, Pageable page);

    boolean existsByMissionId(Long missionId);

    List<SimulationRun> findByStatusIn(Collection<SimulationStatus> statuses);

    /** Row lock; used by the executor so persisting a batch is serialised against cancel/pause/finish. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SimulationRun r where r.id = :id and r.status = :status")
    Optional<SimulationRun> lockByIdAndStatus(@Param("id") Long id, @Param("status") SimulationStatus status);

    // ---- conditional transitions: each returns the number of rows changed (0 = precondition not met) ----

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SimulationRun r set r.status = com.example.camsat.domain.SimulationStatus.RUNNING, r.startedAt = :now, r.progressPercent = 0, r.elapsedSimSeconds = 0 "
            + "where r.id = :id and r.userId = :userId and r.status = com.example.camsat.domain.SimulationStatus.CREATED")
    int markStarted(@Param("id") Long id, @Param("userId") Long userId, @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SimulationRun r set r.status = com.example.camsat.domain.SimulationStatus.CREATED, r.startedAt = null, r.progressPercent = null, r.elapsedSimSeconds = null "
            + "where r.id = :id and r.status = com.example.camsat.domain.SimulationStatus.RUNNING")
    int revertStart(@Param("id") Long id);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SimulationRun r set r.status = com.example.camsat.domain.SimulationStatus.PAUSED where r.id = :id and r.userId = :userId and r.status = com.example.camsat.domain.SimulationStatus.RUNNING")
    int markPaused(@Param("id") Long id, @Param("userId") Long userId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SimulationRun r set r.status = com.example.camsat.domain.SimulationStatus.RUNNING where r.id = :id and r.userId = :userId and r.status = com.example.camsat.domain.SimulationStatus.PAUSED")
    int markResumed(@Param("id") Long id, @Param("userId") Long userId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SimulationRun r set r.status = com.example.camsat.domain.SimulationStatus.CANCELLED, r.endedAt = :now, r.finalResult = :result "
            + "where r.id = :id and r.userId = :userId and r.status in (com.example.camsat.domain.SimulationStatus.CREATED,com.example.camsat.domain.SimulationStatus.RUNNING,com.example.camsat.domain.SimulationStatus.PAUSED)")
    int markCancelled(@Param("id") Long id, @Param("userId") Long userId, @Param("now") Instant now, @Param("result") String result);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SimulationRun r set r.status = :to, r.endedAt = :now, r.landingTimeSeconds = :landingTime, "
            + "r.landingDriftM = :drift, r.finalResult = :result, r.elapsedSimSeconds = :elapsed, r.progressPercent = :progress "
            + "where r.id = :id and r.status in (com.example.camsat.domain.SimulationStatus.RUNNING,com.example.camsat.domain.SimulationStatus.PAUSED)")
    int markFinished(@Param("id") Long id, @Param("to") SimulationStatus to, @Param("now") Instant now,
                     @Param("landingTime") Double landingTime, @Param("drift") Double drift, @Param("result") String result,
                     @Param("elapsed") Double elapsed, @Param("progress") Double progress);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SimulationRun r set r.elapsedSimSeconds = :elapsed, r.progressPercent = :progress "
            + "where r.id = :id and r.status in (com.example.camsat.domain.SimulationStatus.RUNNING,com.example.camsat.domain.SimulationStatus.PAUSED)")
    int updateProgress(@Param("id") Long id, @Param("elapsed") Double elapsed, @Param("progress") Double progress);

    // ---- aggregates, always scoped to one user ----

    @Query("select r.status, count(r) from SimulationRun r where r.userId = :userId group by r.status")
    List<Object[]> countByStatus(@Param("userId") Long userId);

    @Query("select new com.example.camsat.dto.DashboardRecentRun(r.id, r.missionId, m.name, r.status, r.startedAt, r.endedAt, "
            + "r.landingTimeSeconds, r.landingDriftM, r.finalResult) "
            + "from SimulationRun r join Mission m on m.id = r.missionId where r.userId = :userId order by r.id desc")
    List<DashboardRecentRun> recent(@Param("userId") Long userId, Pageable page);

    @Query("select avg(r.landingTimeSeconds) from SimulationRun r where r.userId = :userId and r.status = com.example.camsat.domain.SimulationStatus.COMPLETED and r.landingTimeSeconds is not null")
    Double averageLandingTime(@Param("userId") Long userId);

    @Query("select avg(r.landingDriftM) from SimulationRun r where r.userId = :userId and r.status = com.example.camsat.domain.SimulationStatus.COMPLETED and r.landingDriftM is not null")
    Double averageLandingDrift(@Param("userId") Long userId);

    /** Mean over COMPLETED runs of each run's own maximum descent speed (m/s, positive). */
    @Query(value = "select avg(x.m) from (select max(0 - t.vertical_velocity_mps) as m from telemetry_readings t "
            + "join simulation_runs r on r.id = t.run_id where r.user_id = :userId and r.status = 'COMPLETED' group by t.run_id) x",
            nativeQuery = true)
    Double averageMaxDescentSpeed(@Param("userId") Long userId);
}
package com.example.camsat.repository;

import com.example.camsat.domain.FlightEvent;
import com.example.camsat.domain.FlightEventType;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightEventRepository extends JpaRepository<FlightEvent, Long> {
    List<FlightEvent> findByRunIdOrderByElapsedTimeSecondsAscIdAsc(Long runId);
    List<FlightEvent> findTop5ByRunIdOrderByElapsedTimeSecondsDescIdDesc(Long runId);
    long countByRunId(Long runId);
    boolean existsByRunIdAndDedupeKey(Long runId, String dedupeKey);
    boolean existsByRunIdAndEventType(Long runId, FlightEventType type);
    long countByRunIdAndEventType(Long runId, FlightEventType type);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FlightEvent e where e.runId = :runId and e.dedupeKey = :key")
    int deleteByRunIdAndDedupeKey(@Param("runId") Long runId, @Param("key") String key);
}
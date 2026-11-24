package com.example.camsat.repository;

import com.example.camsat.domain.Mission;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissionRepository extends JpaRepository<Mission, Long> {
    Optional<Mission> findByIdAndUserId(Long id, Long userId);

    List<Mission> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @Query("select m from Mission m where m.userId = :userId and lower(m.name) like lower(concat('%', :search, '%')) "
            + "order by m.createdAt desc, m.id desc")
    List<Mission> search(@Param("userId") Long userId, @Param("search") String search);

    /** Row lock: serialises mission edits/deletes against creation of new runs for the same mission. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mission m where m.id = :id and m.userId = :userId")
    Optional<Mission> lockByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    long countByUserId(Long userId);
}

package com.unicompanion.learning.rag;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QaTurnRepository extends JpaRepository<QaTurnEntity, UUID> {

    List<QaTurnEntity> findByCourseIdAndUserIdOrderByCreatedAtDesc(UUID courseId, UUID userId, Pageable pageable);

    long countByCourseId(UUID courseId);

    @Query("select coalesce(avg(t.latencyMs), 0) from QaTurnEntity t where t.courseId = :courseId")
    double averageLatencyMs(@Param("courseId") UUID courseId);

    @Query("""
            select coalesce(avg(case when t.citedCount > 0 then 1.0 else 0.0 end), 0)
            from QaTurnEntity t
            where t.courseId = :courseId
            """)
    double citeRate(@Param("courseId") UUID courseId);

    @Query("""
            select coalesce(avg(case when t.insufficient = true then 1.0 else 0.0 end), 0)
            from QaTurnEntity t
            where t.courseId = :courseId
            """)
    double insufficientRate(@Param("courseId") UUID courseId);
}

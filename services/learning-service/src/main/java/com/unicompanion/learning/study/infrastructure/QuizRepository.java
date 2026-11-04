package com.unicompanion.learning.study.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<QuizEntity, UUID> {

    List<QuizEntity> findByCourseIdAndMaterialsKeyAndActiveTrueOrderByCreatedAtAsc(
            UUID courseId,
            String materialsKey
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE quizzes
            SET active = false,
                retired_at = :retiredAt,
                retired_reason = :reason
            WHERE active = true
              AND id IN (
                SELECT quiz_id FROM quiz_materials WHERE material_id = :materialId
              )
            """, nativeQuery = true)
    void retireForMaterial(
            @Param("materialId") UUID materialId,
            @Param("retiredAt") Instant retiredAt,
            @Param("reason") String reason
    );
}

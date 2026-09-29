package com.unicompanion.learning.study.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<QuizEntity, UUID> {

    List<QuizEntity> findByCourseIdAndMaterialsKeyAndActiveTrueOrderByCreatedAtAsc(
            UUID courseId,
            String materialsKey
    );
}

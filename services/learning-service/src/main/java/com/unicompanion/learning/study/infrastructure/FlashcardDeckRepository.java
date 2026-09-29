package com.unicompanion.learning.study.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FlashcardDeckRepository extends JpaRepository<FlashcardDeckEntity, UUID> {

    List<FlashcardDeckEntity> findByCourseIdAndMaterialsKeyAndActiveTrueOrderByCreatedAtAsc(
            UUID courseId,
            String materialsKey
    );
}

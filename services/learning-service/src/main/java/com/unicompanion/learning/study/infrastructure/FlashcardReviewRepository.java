package com.unicompanion.learning.study.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FlashcardReviewRepository extends JpaRepository<FlashcardReviewEntity, FlashcardReviewEntity.Key> {

    List<FlashcardReviewEntity> findByCourseIdAndUserIdAndStatus(UUID courseId, UUID userId, String status);
}

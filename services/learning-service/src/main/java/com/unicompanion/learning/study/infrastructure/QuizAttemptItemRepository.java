package com.unicompanion.learning.study.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface QuizAttemptItemRepository extends JpaRepository<QuizAttemptItemEntity, UUID> {

    List<QuizAttemptItemEntity> findByAttemptIdIn(Collection<UUID> attemptIds);
}

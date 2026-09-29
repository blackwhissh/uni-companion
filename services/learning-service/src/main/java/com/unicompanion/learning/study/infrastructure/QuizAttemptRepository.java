package com.unicompanion.learning.study.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttemptEntity, UUID> {
}

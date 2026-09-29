package com.unicompanion.learning.study.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestionEntity, UUID> {

    List<QuizQuestionEntity> findByQuizIdOrderBySortOrderAsc(UUID quizId);
}

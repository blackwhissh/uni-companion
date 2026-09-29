package com.unicompanion.learning.study.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizProgressRepository extends JpaRepository<QuizProgressEntity, StudyProgressKey> {
}

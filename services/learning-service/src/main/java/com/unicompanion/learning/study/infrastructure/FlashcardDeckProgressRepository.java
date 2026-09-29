package com.unicompanion.learning.study.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashcardDeckProgressRepository extends JpaRepository<FlashcardDeckProgressEntity, StudyProgressKey> {
}

package com.unicompanion.learning.study.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FlashcardRepository extends JpaRepository<FlashcardEntity, UUID> {

    List<FlashcardEntity> findByDeckIdOrderBySortOrderAsc(UUID deckId);
}

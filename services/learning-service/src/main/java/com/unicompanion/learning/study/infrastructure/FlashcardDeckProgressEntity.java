package com.unicompanion.learning.study.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flashcard_deck_progress")
public class FlashcardDeckProgressEntity {

    @EmbeddedId
    private StudyProgressKey id;

    @Column(name = "deck_id", nullable = false)
    private UUID deckId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FlashcardDeckProgressEntity() {
    }

    public static FlashcardDeckProgressEntity create(StudyProgressKey id, UUID deckId, Instant updatedAt) {
        FlashcardDeckProgressEntity progress = new FlashcardDeckProgressEntity();
        progress.id = id;
        progress.deckId = deckId;
        progress.updatedAt = updatedAt;
        return progress;
    }

    public StudyProgressKey getId() {
        return id;
    }

    public UUID getDeckId() {
        return deckId;
    }

    public void assign(UUID deckId, Instant updatedAt) {
        this.deckId = deckId;
        this.updatedAt = updatedAt;
    }
}

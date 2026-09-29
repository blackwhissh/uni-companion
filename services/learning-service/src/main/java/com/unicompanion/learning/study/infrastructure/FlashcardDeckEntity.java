package com.unicompanion.learning.study.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flashcard_decks")
public class FlashcardDeckEntity {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "materials_key", nullable = false, length = 512)
    private String materialsKey;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "retired_reason", length = 500)
    private String retiredReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FlashcardDeckEntity() {
    }

    public static FlashcardDeckEntity create(
            UUID id,
            UUID courseId,
            UUID userId,
            String materialsKey,
            Instant createdAt
    ) {
        FlashcardDeckEntity deck = new FlashcardDeckEntity();
        deck.id = id;
        deck.courseId = courseId;
        deck.userId = userId;
        deck.materialsKey = materialsKey;
        deck.active = true;
        deck.createdAt = createdAt;
        return deck;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getMaterialsKey() {
        return materialsKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return active;
    }

    public void retire(String reason, Instant now) {
        this.active = false;
        this.retiredAt = now;
        this.retiredReason = reason;
    }
}

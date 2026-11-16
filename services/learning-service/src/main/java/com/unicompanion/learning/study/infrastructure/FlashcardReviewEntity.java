package com.unicompanion.learning.study.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "flashcard_reviews")
@IdClass(FlashcardReviewEntity.Key.class)
public class FlashcardReviewEntity {

    public static final String AGAIN = "AGAIN";
    public static final String KNOWN = "KNOWN";

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false, length = 500)
    private String front;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FlashcardReviewEntity() {
    }

    public static FlashcardReviewEntity create(
            UUID userId,
            UUID cardId,
            UUID courseId,
            String front,
            String status,
            Instant now
    ) {
        FlashcardReviewEntity review = new FlashcardReviewEntity();
        review.userId = userId;
        review.cardId = cardId;
        review.courseId = courseId;
        review.front = front;
        review.status = status;
        review.updatedAt = now;
        return review;
    }

    public void revise(UUID courseId, String front, String status, Instant now) {
        this.courseId = courseId;
        this.front = front;
        this.status = status;
        this.updatedAt = now;
    }

    public String getFront() {
        return front;
    }

    public String getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static final class Key implements Serializable {
        public UUID userId;
        public UUID cardId;

        public Key() {
        }

        public Key(UUID userId, UUID cardId) {
            this.userId = userId;
            this.cardId = cardId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(userId, key.userId) && Objects.equals(cardId, key.cardId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, cardId);
        }
    }
}

package com.unicompanion.learning.study.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quiz_attempts")
public class QuizAttemptEntity {

    @Id
    private UUID id;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int total;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QuizAttemptEntity() {
    }

    public static QuizAttemptEntity create(
            UUID id,
            UUID quizId,
            UUID userId,
            int score,
            int total,
            Instant createdAt
    ) {
        QuizAttemptEntity attempt = new QuizAttemptEntity();
        attempt.id = id;
        attempt.quizId = quizId;
        attempt.userId = userId;
        attempt.score = score;
        attempt.total = total;
        attempt.createdAt = createdAt;
        return attempt;
    }

    public UUID getId() {
        return id;
    }

    public int getScore() {
        return score;
    }

    public int getTotal() {
        return total;
    }
}

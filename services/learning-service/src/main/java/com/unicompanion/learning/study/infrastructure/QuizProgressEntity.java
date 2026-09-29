package com.unicompanion.learning.study.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quiz_progress")
public class QuizProgressEntity {

    @EmbeddedId
    private StudyProgressKey id;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuizProgressEntity() {
    }

    public static QuizProgressEntity create(StudyProgressKey id, UUID quizId, Instant updatedAt) {
        QuizProgressEntity progress = new QuizProgressEntity();
        progress.id = id;
        progress.quizId = quizId;
        progress.updatedAt = updatedAt;
        return progress;
    }

    public StudyProgressKey getId() {
        return id;
    }

    public UUID getQuizId() {
        return quizId;
    }

    public void assign(UUID quizId, Instant updatedAt) {
        this.quizId = quizId;
        this.updatedAt = updatedAt;
    }
}

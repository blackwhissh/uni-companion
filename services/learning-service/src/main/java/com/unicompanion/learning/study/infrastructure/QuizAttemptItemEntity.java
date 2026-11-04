package com.unicompanion.learning.study.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "quiz_attempt_items")
public class QuizAttemptItemEntity {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected QuizAttemptItemEntity() {
    }

    public static QuizAttemptItemEntity create(
            UUID id,
            UUID attemptId,
            String prompt,
            boolean correct,
            int sortOrder
    ) {
        QuizAttemptItemEntity item = new QuizAttemptItemEntity();
        item.id = id;
        item.attemptId = attemptId;
        item.prompt = prompt == null ? "" : prompt;
        item.correct = correct;
        item.sortOrder = sortOrder;
        return item;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public String getPrompt() {
        return prompt;
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}

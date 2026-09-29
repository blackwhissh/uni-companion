package com.unicompanion.learning.study.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestionEntity {

    @Id
    private UUID id;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(name = "options_json", nullable = false, columnDefinition = "text")
    private String optionsJson;

    @Column(name = "correct_index", nullable = false)
    private int correctIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected QuizQuestionEntity() {
    }

    public static QuizQuestionEntity create(
            UUID id,
            UUID quizId,
            String prompt,
            String optionsJson,
            int correctIndex,
            String explanation,
            int sortOrder
    ) {
        QuizQuestionEntity question = new QuizQuestionEntity();
        question.id = id;
        question.quizId = quizId;
        question.prompt = prompt;
        question.optionsJson = optionsJson;
        question.correctIndex = correctIndex;
        question.explanation = explanation == null ? "" : explanation;
        question.sortOrder = sortOrder;
        return question;
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuizId() {
        return quizId;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public int getCorrectIndex() {
        return correctIndex;
    }

    public String getExplanation() {
        return explanation;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}

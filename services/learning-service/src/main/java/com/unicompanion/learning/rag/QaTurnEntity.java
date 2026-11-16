package com.unicompanion.learning.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rag_qa_turns")
public class QaTurnEntity {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    @Column(name = "citations_json", nullable = false, columnDefinition = "text")
    private String citationsJson;

    @Column(name = "material_ids_json", nullable = false, columnDefinition = "text")
    private String materialIdsJson;

    @Column(name = "retrieved_count", nullable = false)
    private int retrievedCount;

    @Column(name = "cited_count", nullable = false)
    private int citedCount;

    @Column(nullable = false)
    private boolean insufficient;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "embed_latency_ms", nullable = false)
    private int embedLatencyMs;

    @Column(name = "answer_latency_ms", nullable = false)
    private int answerLatencyMs;

    @Column(name = "model_available", nullable = false)
    private boolean modelAvailable;

    @Column(name = "retrieved_json", nullable = false, columnDefinition = "text")
    private String retrievedJson;

    @Column(name = "follow_up", nullable = false)
    private boolean followUp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QaTurnEntity() {
    }

    public static QaTurnEntity create(
            UUID id,
            UUID courseId,
            UUID userId,
            String question,
            String answer,
            String citationsJson,
            String materialIdsJson,
            int retrievedCount,
            int citedCount,
            boolean insufficient,
            int latencyMs,
            int embedLatencyMs,
            int answerLatencyMs,
            boolean modelAvailable,
            String retrievedJson,
            boolean followUp,
            Instant createdAt
    ) {
        QaTurnEntity turn = new QaTurnEntity();
        turn.id = id;
        turn.courseId = courseId;
        turn.userId = userId;
        turn.question = question;
        turn.answer = answer;
        turn.citationsJson = citationsJson;
        turn.materialIdsJson = materialIdsJson;
        turn.retrievedCount = retrievedCount;
        turn.citedCount = citedCount;
        turn.insufficient = insufficient;
        turn.latencyMs = latencyMs;
        turn.embedLatencyMs = embedLatencyMs;
        turn.answerLatencyMs = answerLatencyMs;
        turn.modelAvailable = modelAvailable;
        turn.retrievedJson = retrievedJson == null ? "[]" : retrievedJson;
        turn.followUp = followUp;
        turn.createdAt = createdAt;
        return turn;
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

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public String getCitationsJson() {
        return citationsJson;
    }

    public String getMaterialIdsJson() {
        return materialIdsJson;
    }

    public int getRetrievedCount() {
        return retrievedCount;
    }

    public int getCitedCount() {
        return citedCount;
    }

    public boolean isInsufficient() {
        return insufficient;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public int getEmbedLatencyMs() {
        return embedLatencyMs;
    }

    public int getAnswerLatencyMs() {
        return answerLatencyMs;
    }

    public boolean isModelAvailable() {
        return modelAvailable;
    }

    public String getRetrievedJson() {
        return retrievedJson;
    }

    public boolean isFollowUp() {
        return followUp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

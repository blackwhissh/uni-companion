package com.unicompanion.learning.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "study_chat_messages")
public class StudyChatMessageEntity {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citations_json", columnDefinition = "jsonb")
    private List<StoredCitation> citations = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StudyChatMessageEntity() {
    }

    public static StudyChatMessageEntity user(UUID id, UUID sessionId, String content, Instant now) {
        StudyChatMessageEntity message = new StudyChatMessageEntity();
        message.id = id;
        message.sessionId = sessionId;
        message.role = ROLE_USER;
        message.content = content;
        message.citations = List.of();
        message.createdAt = now;
        return message;
    }

    public static StudyChatMessageEntity assistant(
            UUID id,
            UUID sessionId,
            String content,
            List<StoredCitation> citations,
            Instant now
    ) {
        StudyChatMessageEntity message = new StudyChatMessageEntity();
        message.id = id;
        message.sessionId = sessionId;
        message.role = ROLE_ASSISTANT;
        message.content = content;
        message.citations = citations == null ? List.of() : List.copyOf(citations);
        message.createdAt = now;
        return message;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public List<StoredCitation> getCitations() {
        return citations == null ? List.of() : citations;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public record StoredCitation(
            UUID materialId,
            String title,
            String sectionHint,
            int pageNumber,
            String excerpt
    ) {
    }
}

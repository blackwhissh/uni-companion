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
@Table(name = "study_chat_sessions")
public class StudyChatSessionEntity {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "material_ids", nullable = false, columnDefinition = "jsonb")
    private List<UUID> materialIds = new ArrayList<>();

    @Column(length = 2)
    private String language;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudyChatSessionEntity() {
    }

    public static StudyChatSessionEntity create(
            UUID id,
            UUID courseId,
            UUID userId,
            List<UUID> materialIds,
            String language,
            Instant now
    ) {
        StudyChatSessionEntity session = new StudyChatSessionEntity();
        session.id = id;
        session.courseId = courseId;
        session.userId = userId;
        session.materialIds = List.copyOf(materialIds);
        session.language = language;
        session.createdAt = now;
        session.updatedAt = now;
        return session;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
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

    public List<UUID> getMaterialIds() {
        return materialIds;
    }

    public String getLanguage() {
        return language;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

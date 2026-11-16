package com.unicompanion.learning.lms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_lms_links")
public class CourseLmsLinkEntity {

    @Id
    @Column(name = "course_id")
    private UUID courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LmsProvider provider;

    @Column(name = "external_course_id", nullable = false, length = 128)
    private String externalCourseId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_status", length = 32)
    private String lastStatus;

    @Column(name = "last_message", length = 500)
    private String lastMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseLmsLinkEntity() {
    }

    public static CourseLmsLinkEntity create(
            UUID courseId,
            LmsProvider provider,
            String externalCourseId,
            Instant now
    ) {
        CourseLmsLinkEntity link = new CourseLmsLinkEntity();
        link.courseId = courseId;
        link.provider = provider;
        link.externalCourseId = externalCourseId.trim();
        link.enabled = true;
        link.createdAt = now;
        link.updatedAt = now;
        return link;
    }

    public void update(LmsProvider provider, String externalCourseId, boolean enabled, Instant now) {
        this.provider = provider;
        this.externalCourseId = externalCourseId.trim();
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public void recordSync(String status, String message, Instant now) {
        this.lastSyncedAt = now;
        this.lastStatus = status;
        this.lastMessage = message == null || message.length() <= 500 ? message : message.substring(0, 500);
        this.updatedAt = now;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public LmsProvider getProvider() {
        return provider;
    }

    public String getExternalCourseId() {
        return externalCourseId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public String getLastMessage() {
        return lastMessage;
    }
}

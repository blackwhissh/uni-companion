package com.unicompanion.learning.matching.infrastructure;

import com.unicompanion.learning.matching.domain.ContactStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contact_requests")
public class ContactRequestEntity {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContactStatus status;

    @Column(name = "requester_name", nullable = false, length = 120)
    private String requesterName;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(name = "requester_email", length = 320)
    private String requesterEmail;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected ContactRequestEntity() {
    }

    public static ContactRequestEntity pending(
            UUID id,
            UUID courseId,
            UUID requesterId,
            UUID recipientId,
            String requesterName,
            String recipientName,
            Instant createdAt
    ) {
        ContactRequestEntity entity = new ContactRequestEntity();
        entity.id = id;
        entity.courseId = courseId;
        entity.requesterId = requesterId;
        entity.recipientId = recipientId;
        entity.status = ContactStatus.PENDING;
        entity.requesterName = requesterName;
        entity.recipientName = recipientName;
        entity.createdAt = createdAt;
        return entity;
    }

    public void reopen(String requesterName, String recipientName) {
        this.status = ContactStatus.PENDING;
        this.requesterName = requesterName;
        this.recipientName = recipientName;
        this.requesterEmail = null;
        this.recipientEmail = null;
        this.decidedAt = null;
    }

    public void accept(String requesterName, String recipientName, String requesterEmail, String recipientEmail, Instant decidedAt) {
        this.status = ContactStatus.ACCEPTED;
        this.requesterName = requesterName;
        this.recipientName = recipientName;
        this.requesterEmail = requesterEmail;
        this.recipientEmail = recipientEmail;
        this.decidedAt = decidedAt;
    }

    public void decline(Instant decidedAt) {
        this.status = ContactStatus.DECLINED;
        this.requesterEmail = null;
        this.recipientEmail = null;
        this.decidedAt = decidedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public ContactStatus getStatus() {
        return status;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }
}

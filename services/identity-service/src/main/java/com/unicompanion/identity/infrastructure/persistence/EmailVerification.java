package com.unicompanion.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verifications")
public class EmailVerification {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailVerification() {
    }

    public static EmailVerification issue(
            UUID id,
            UUID userId,
            String email,
            String codeHash,
            Instant expiresAt,
            Instant now
    ) {
        EmailVerification challenge = new EmailVerification();
        challenge.id = id;
        challenge.userId = userId;
        challenge.email = email;
        challenge.codeHash = codeHash;
        challenge.attemptCount = 0;
        challenge.expiresAt = expiresAt;
        challenge.createdAt = now;
        return challenge;
    }

    public void recordMiss() {
        this.attemptCount++;
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isOpen() {
        return consumedAt == null;
    }
}

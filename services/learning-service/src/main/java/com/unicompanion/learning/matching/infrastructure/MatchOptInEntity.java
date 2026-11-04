package com.unicompanion.learning.matching.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "match_opt_ins")
public class MatchOptInEntity {

    @EmbeddedId
    private MatchOptInKey id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;

    protected MatchOptInEntity() {
    }

    public static MatchOptInEntity create(MatchOptInKey id, Instant createdAt, String displayName, String contactEmail) {
        MatchOptInEntity entity = new MatchOptInEntity();
        entity.id = id;
        entity.createdAt = createdAt;
        entity.displayName = displayName;
        entity.contactEmail = contactEmail;
        return entity;
    }

    public void updateProfile(String displayName, String contactEmail) {
        this.displayName = displayName;
        this.contactEmail = contactEmail;
    }

    public MatchOptInKey getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getContactEmail() {
        return contactEmail;
    }
}

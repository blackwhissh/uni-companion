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

    protected MatchOptInEntity() {
    }

    public static MatchOptInEntity create(MatchOptInKey id, Instant createdAt, String displayName) {
        MatchOptInEntity entity = new MatchOptInEntity();
        entity.id = id;
        entity.createdAt = createdAt;
        entity.displayName = displayName;
        return entity;
    }

    public void rename(String displayName) {
        this.displayName = displayName;
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
}

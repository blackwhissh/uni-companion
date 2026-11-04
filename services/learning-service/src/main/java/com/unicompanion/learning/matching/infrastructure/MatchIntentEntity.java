package com.unicompanion.learning.matching.infrastructure;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "match_intents")
public class MatchIntentEntity {

    @EmbeddedId
    private MatchIntentKey id;

    protected MatchIntentEntity() {
    }

    public static MatchIntentEntity create(MatchIntentKey id) {
        MatchIntentEntity entity = new MatchIntentEntity();
        entity.id = id;
        return entity;
    }

    public MatchIntentKey getId() {
        return id;
    }
}

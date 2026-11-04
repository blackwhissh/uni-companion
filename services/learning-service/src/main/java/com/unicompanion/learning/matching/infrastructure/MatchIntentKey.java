package com.unicompanion.learning.matching.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MatchIntentKey implements Serializable {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String intent;

    protected MatchIntentKey() {
    }

    public MatchIntentKey(UUID courseId, UUID userId, String intent) {
        this.courseId = courseId;
        this.userId = userId;
        this.intent = intent;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getIntent() {
        return intent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MatchIntentKey that)) {
            return false;
        }
        return Objects.equals(courseId, that.courseId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(intent, that.intent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courseId, userId, intent);
    }
}

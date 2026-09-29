package com.unicompanion.learning.matching.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MatchOptInKey implements Serializable {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected MatchOptInKey() {
    }

    public MatchOptInKey(UUID courseId, UUID userId) {
        this.courseId = courseId;
        this.userId = userId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MatchOptInKey that)) {
            return false;
        }
        return Objects.equals(courseId, that.courseId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courseId, userId);
    }
}

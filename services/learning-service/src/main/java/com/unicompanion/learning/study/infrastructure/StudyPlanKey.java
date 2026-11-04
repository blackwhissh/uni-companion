package com.unicompanion.learning.study.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class StudyPlanKey implements Serializable {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected StudyPlanKey() {
    }

    public StudyPlanKey(UUID courseId, UUID userId) {
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
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StudyPlanKey key)) {
            return false;
        }
        return courseId.equals(key.courseId) && userId.equals(key.userId);
    }

    @Override
    public int hashCode() {
        return courseId.hashCode() * 31 + userId.hashCode();
    }
}

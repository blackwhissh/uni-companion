package com.unicompanion.learning.course.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class EnrollmentKey implements Serializable {

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "user_id")
    private UUID userId;

    protected EnrollmentKey() {
    }

    public EnrollmentKey(UUID courseId, UUID userId) {
        this.courseId = courseId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EnrollmentKey key)) {
            return false;
        }
        return courseId.equals(key.courseId) && userId.equals(key.userId);
    }

    @Override
    public int hashCode() {
        return courseId.hashCode() * 31 + userId.hashCode();
    }
}

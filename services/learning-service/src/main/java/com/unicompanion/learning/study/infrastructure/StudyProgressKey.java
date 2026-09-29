package com.unicompanion.learning.study.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class StudyProgressKey implements Serializable {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "materials_key", nullable = false, length = 512)
    private String materialsKey;

    protected StudyProgressKey() {
    }

    public StudyProgressKey(UUID courseId, UUID userId, String materialsKey) {
        this.courseId = courseId;
        this.userId = userId;
        this.materialsKey = materialsKey;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getMaterialsKey() {
        return materialsKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StudyProgressKey key)) {
            return false;
        }
        return Objects.equals(courseId, key.courseId)
                && Objects.equals(userId, key.userId)
                && Objects.equals(materialsKey, key.materialsKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courseId, userId, materialsKey);
    }
}

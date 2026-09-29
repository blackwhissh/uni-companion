package com.unicompanion.learning.course.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "enrollments")
public class EnrollmentEntity {

    @EmbeddedId
    private EnrollmentKey id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EnrollmentEntity() {
    }

    public static EnrollmentEntity create(EnrollmentKey id, Instant now) {
        EnrollmentEntity enrollment = new EnrollmentEntity();
        enrollment.id = id;
        enrollment.createdAt = now;
        return enrollment;
    }
}

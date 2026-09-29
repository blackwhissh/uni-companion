package com.unicompanion.learning.course.infrastructure;

import com.unicompanion.learning.course.domain.Visibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courses")
public class CourseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String term;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Visibility visibility;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseEntity() {
    }

    public static CourseEntity create(UUID id, String title, String code, String term, UUID ownerId, Instant now) {
        CourseEntity course = new CourseEntity();
        course.id = id;
        course.title = title.trim();
        course.code = code.trim();
        course.term = term.trim();
        course.visibility = Visibility.UNPUBLISHED;
        course.ownerId = ownerId;
        course.createdAt = now;
        course.updatedAt = now;
        return course;
    }

    public void changeVisibility(Visibility visibility, Instant now) {
        this.visibility = visibility;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCode() {
        return code;
    }

    public String getTerm() {
        return term;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public boolean isPublished() {
        return visibility == Visibility.PUBLISHED;
    }
}

package com.unicompanion.learning.study.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "study_plans")
public class StudyPlanEntity {

    @EmbeddedId
    private StudyPlanKey id;

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudyPlanEntity() {
    }

    public static StudyPlanEntity create(StudyPlanKey id, LocalDate examDate, Instant updatedAt) {
        StudyPlanEntity entity = new StudyPlanEntity();
        entity.id = id;
        entity.examDate = examDate;
        entity.updatedAt = updatedAt;
        return entity;
    }

    public void reschedule(LocalDate examDate, Instant updatedAt) {
        this.examDate = examDate;
        this.updatedAt = updatedAt;
    }

    public LocalDate getExamDate() {
        return examDate;
    }
}

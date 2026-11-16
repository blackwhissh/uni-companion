package com.unicompanion.learning.material.infrastructure;

import com.unicompanion.learning.course.domain.Visibility;
import com.unicompanion.learning.material.domain.ProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "materials")
public class MaterialEntity {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 32)
    private ProcessingStatus processingStatus;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "source_provider", length = 32)
    private String sourceProvider;

    @Column(name = "source_external_id", length = 128)
    private String sourceExternalId;

    @Column(name = "source_checksum", length = 128)
    private String sourceChecksum;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MaterialEntity() {
    }

    public static MaterialEntity create(UUID id, UUID courseId, String title, String fileName, String storagePath, Instant now) {
        MaterialEntity material = new MaterialEntity();
        material.id = id;
        material.courseId = courseId;
        material.title = title;
        material.fileName = fileName;
        material.storagePath = storagePath;
        material.visibility = Visibility.UNPUBLISHED;
        material.processingStatus = ProcessingStatus.UPLOADED;
        material.createdAt = now;
        material.updatedAt = now;
        return material;
    }

    public static MaterialEntity createFromLms(
            UUID id,
            UUID courseId,
            String title,
            String fileName,
            String storagePath,
            String sourceProvider,
            String sourceExternalId,
            String sourceChecksum,
            Instant now
    ) {
        MaterialEntity material = create(id, courseId, title, fileName, storagePath, now);
        material.sourceProvider = sourceProvider;
        material.sourceExternalId = sourceExternalId;
        material.sourceChecksum = sourceChecksum;
        return material;
    }

    public void markProcessing(Instant now) {
        this.processingStatus = ProcessingStatus.PROCESSING;
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void markReady(Instant now) {
        this.processingStatus = ProcessingStatus.READY;
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        this.processingStatus = ProcessingStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public void resetForRetry(Instant now) {
        this.processingStatus = ProcessingStatus.UPLOADED;
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void changeVisibility(Visibility visibility, Instant now) {
        this.visibility = visibility;
        this.updatedAt = now;
    }

    public void replaceFile(String title, String fileName, String storagePath, Instant now) {
        this.title = title;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.processingStatus = ProcessingStatus.UPLOADED;
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void replaceFromLms(
            String title,
            String fileName,
            String storagePath,
            String sourceChecksum,
            Instant now
    ) {
        replaceFile(title, fileName, storagePath, now);
        this.sourceChecksum = sourceChecksum;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getTitle() {
        return title;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public String getSourceExternalId() {
        return sourceExternalId;
    }

    public String getSourceChecksum() {
        return sourceChecksum;
    }
}

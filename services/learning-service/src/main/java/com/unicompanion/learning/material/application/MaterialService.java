package com.unicompanion.learning.material.application;

import com.unicompanion.learning.course.domain.Visibility;
import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
import com.unicompanion.learning.material.domain.ProcessingStatus;
import com.unicompanion.learning.material.infrastructure.LocalPdfStorage;
import com.unicompanion.learning.material.infrastructure.MaterialChunkRepository;
import com.unicompanion.learning.material.infrastructure.MaterialEntity;
import com.unicompanion.learning.material.infrastructure.MaterialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MaterialService {

    private static final Logger log = LoggerFactory.getLogger(MaterialService.class);

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final MaterialRepository materials;
    private final MaterialChunkRepository chunks;
    private final LocalPdfStorage storage;

    public MaterialService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            MaterialRepository materials,
            MaterialChunkRepository chunks,
            LocalPdfStorage storage
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.materials = materials;
        this.chunks = chunks;
        this.storage = storage;
    }

    @Transactional
    public MaterialEntity accept(
            UUID userId,
            UUID courseId,
            String title,
            String fileName,
            String contentType,
            byte[] bytes,
            boolean platformAdmin
    ) {
        CourseEntity course = courses.findById(courseId).orElseThrow(MaterialService::notFound);
        boolean owner = course.getOwnerId().equals(userId);
        if (!owner && !platformAdmin) {
            log.warn("Upload denied: user={} is not owner of course={}", userId, courseId);
            throw forbidden();
        }
        String safeName = fileName(fileName);
        if (bytes.length == 0 || !safeName.toLowerCase(Locale.ROOT).endsWith(".pdf") || !isPdf(contentType)) {
            log.warn("Upload rejected: invalid PDF course={} fileName={} contentType={} bytes={}",
                    courseId, safeName, contentType, bytes.length);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a PDF file.");
        }
        String materialTitle = materialTitle(title, safeName);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String storagePath = storage.store(id, safeName, bytes);
        MaterialEntity saved = materials.save(MaterialEntity.create(id, courseId, materialTitle, safeName, storagePath, now));
        log.info(
                "Accepted material id={} course={} fileName={} bytes={} by={} owner={} platformAdmin={}",
                saved.getId(),
                courseId,
                safeName,
                bytes.length,
                userId,
                owner,
                platformAdmin
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MaterialEntity> list(UUID courseId, UUID userId, boolean admin) {
        CourseEntity course = courses.findById(courseId).orElseThrow(MaterialService::notFound);
        if (admin) {
            List<MaterialEntity> all = materials.findByCourseIdOrderByTitleAsc(courseId);
            log.debug("Listed {} materials for course admin={} course={} owner={}",
                    all.size(), userId, courseId, course.getOwnerId());
            return all;
        }
        if (!course.isPublished()) {
            throw notFound();
        }
        if (!enrollments.existsById(new EnrollmentKey(courseId, userId))) {
            log.warn("Material list denied: user={} not enrolled in course={}", userId, courseId);
            throw forbidden();
        }
        List<MaterialEntity> visible = materials.findByCourseIdAndVisibilityAndProcessingStatusOrderByTitleAsc(
                courseId,
                Visibility.PUBLISHED,
                ProcessingStatus.READY
        );
        log.debug("Listed {} published materials for student={} course={}", visible.size(), userId, courseId);
        return visible;
    }

    @Transactional(readOnly = true)
    public MaterialFile download(UUID materialId, UUID userId, boolean admin) {
        MaterialEntity material = materials.findById(materialId).orElseThrow(MaterialService::notFound);
        CourseEntity course = courses.findById(material.getCourseId()).orElseThrow(MaterialService::notFound);
        if (!admin) {
            if (!course.isPublished()
                    || material.getVisibility() != Visibility.PUBLISHED
                    || material.getProcessingStatus() != ProcessingStatus.READY) {
                throw notFound();
            }
            if (!enrollments.existsById(new EnrollmentKey(course.getId(), userId))) {
                log.warn("Download denied: user={} not enrolled for material={}", userId, materialId);
                throw forbidden();
            }
        }
        try {
            MaterialFile file = new MaterialFile(material.getFileName(), storage.read(material.getStoragePath()));
            log.info("Download material id={} fileName={} bytes={} user={} admin={}",
                    materialId, file.fileName(), file.bytes().length, userId, admin);
            return file;
        } catch (IOException ex) {
            log.error("Download failed: missing file for material={}", materialId, ex);
            throw notFound();
        }
    }

    @Transactional
    public MaterialEntity changeVisibility(UUID materialId, UUID userId, Visibility visibility, boolean platformAdmin) {
        MaterialEntity material = materials.findById(materialId).orElseThrow(MaterialService::notFound);
        CourseEntity course = courses.findById(material.getCourseId()).orElseThrow(MaterialService::notFound);
        boolean owner = course.getOwnerId().equals(userId);
        if (!owner && !platformAdmin) {
            log.warn("Visibility change denied: user={} material={}", userId, materialId);
            throw forbidden();
        }
        material.changeVisibility(visibility, Instant.now());
        log.info(
                "Material visibility changed id={} visibility={} by={} owner={} platformAdmin={}",
                materialId,
                visibility,
                userId,
                owner,
                platformAdmin
        );
        return material;
    }

    @Transactional
    public void delete(UUID materialId, UUID userId, boolean platformAdmin) {
        MaterialEntity material = materials.findById(materialId).orElseThrow(MaterialService::notFound);
        CourseEntity course = courses.findById(material.getCourseId()).orElseThrow(MaterialService::notFound);
        boolean owner = course.getOwnerId().equals(userId);
        if (!owner && !platformAdmin) {
            log.warn("Delete denied: user={} material={}", userId, materialId);
            throw forbidden();
        }
        chunks.deleteByMaterialId(materialId);
        materials.delete(material);
        storage.delete(materialId);
        log.info(
                "Deleted material id={} course={} by={} owner={} platformAdmin={}",
                materialId,
                course.getId(),
                userId,
                owner,
                platformAdmin
        );
    }

    private static String fileName(String fileName) {
        String safeName = fileName == null ? "" : Path.of(fileName).getFileName().toString();
        if (safeName.isBlank() || safeName.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a PDF file.");
        }
        return safeName;
    }

    private static String materialTitle(String title, String fileName) {
        String value = title == null || title.isBlank() ? fileName.substring(0, fileName.length() - 4) : title.trim();
        if (value.isBlank() || value.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a PDF file.");
        }
        return value;
    }

    private static boolean isPdf(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("application/pdf");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}

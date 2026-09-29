package com.unicompanion.learning.course.application;

import com.unicompanion.learning.course.domain.Visibility;
import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentEntity;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final MaterialRepository materials;
    private final MaterialChunkRepository chunks;
    private final LocalPdfStorage storage;

    public CourseService(
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
    public CourseEntity create(UUID ownerId, String title, String code, String term) {
        CourseEntity course = courses.save(CourseEntity.create(UUID.randomUUID(), title, code, term, ownerId, Instant.now()));
        log.info("Created course id={} code={} owner={}", course.getId(), course.getCode(), ownerId);
        return course;
    }

    @Transactional(readOnly = true)
    public List<CourseDetails> listFor(UUID userId, boolean admin) {
        List<CourseEntity> found = admin
                ? courses.findAllByOrderByCodeAsc()
                : courses.findByVisibilityOrderByCodeAsc(Visibility.PUBLISHED);
        Set<UUID> enrolledIds = Set.copyOf(enrollments.findCourseIdsByUserId(userId));
        log.debug("Listed {} courses for user={} admin={}", found.size(), userId, admin);
        return found.stream()
                .map(course -> details(course, userId, enrolledIds.contains(course.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDetails getVisible(UUID courseId, UUID userId, boolean admin) {
        CourseEntity course = courses.findById(courseId).orElseThrow(CourseService::notFound);
        if (!course.isPublished() && !admin) {
            log.debug("Course {} hidden from non-admin user={}", courseId, userId);
            throw notFound();
        }
        return details(course, userId, enrollments.existsById(new EnrollmentKey(courseId, userId)));
    }

    @Transactional
    public CourseDetails changeVisibility(UUID courseId, Visibility visibility, UUID userId, boolean platformAdmin) {
        CourseEntity course = courses.findById(courseId).orElseThrow(CourseService::notFound);
        boolean owner = course.getOwnerId().equals(userId);
        if (!owner && !platformAdmin) {
            log.warn("Visibility change denied: user={} course={} visibility={}", userId, courseId, visibility);
            throw forbidden();
        }
        course.changeVisibility(visibility, Instant.now());
        log.info(
                "Course visibility changed id={} visibility={} by={} owner={} platformAdmin={}",
                courseId,
                visibility,
                userId,
                owner,
                platformAdmin
        );
        return details(course, userId, enrollments.existsById(new EnrollmentKey(courseId, userId)));
    }

    @Transactional
    public void delete(UUID courseId, UUID userId, boolean platformAdmin) {
        CourseEntity course = courses.findById(courseId).orElseThrow(CourseService::notFound);
        boolean owner = course.getOwnerId().equals(userId);
        if (!owner && !platformAdmin) {
            log.warn("Delete denied: user={} course={}", userId, courseId);
            throw forbidden();
        }
        List<MaterialEntity> courseMaterials = materials.findByCourseIdOrderByTitleAsc(courseId);
        for (MaterialEntity material : courseMaterials) {
            chunks.deleteByMaterialId(material.getId());
            materials.delete(material);
            storage.delete(material.getId());
        }
        enrollments.deleteByIdCourseId(courseId);
        courses.delete(course);
        log.info(
                "Deleted course id={} code={} materials={} by={} owner={} platformAdmin={}",
                courseId,
                course.getCode(),
                courseMaterials.size(),
                userId,
                owner,
                platformAdmin
        );
    }

    @Transactional
    public EnrollmentResult enroll(UUID courseId, UUID userId) {
        CourseEntity course = courses.findById(courseId).orElseThrow(CourseService::notFound);
        if (!course.isPublished()) {
            throw notFound();
        }
        EnrollmentKey key = new EnrollmentKey(courseId, userId);
        if (enrollments.existsById(key)) {
            log.debug("Enrollment already exists course={} user={}", courseId, userId);
            return new EnrollmentResult(courseId, false);
        }
        enrollments.save(EnrollmentEntity.create(key, Instant.now()));
        log.info("Enrolled user={} in course={}", userId, courseId);
        return new EnrollmentResult(courseId, true);
    }

    @Transactional
    public void unenroll(UUID courseId, UUID userId) {
        CourseEntity course = courses.findById(courseId).orElseThrow(CourseService::notFound);
        if (!course.isPublished()) {
            throw notFound();
        }
        enrollments.deleteById(new EnrollmentKey(courseId, userId));
        log.info("Unenrolled user={} from course={}", userId, courseId);
    }

    private static CourseDetails details(CourseEntity course, UUID userId, boolean enrolled) {
        return new CourseDetails(course, enrolled, course.getOwnerId().equals(userId));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public record EnrollmentResult(UUID courseId, boolean created) {
    }

    public record CourseDetails(CourseEntity course, boolean enrolled, boolean owned) {
    }
}

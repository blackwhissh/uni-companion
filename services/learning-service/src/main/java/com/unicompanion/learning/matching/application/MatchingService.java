package com.unicompanion.learning.matching.application;

import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
import com.unicompanion.learning.matching.infrastructure.MatchOptInEntity;
import com.unicompanion.learning.matching.infrastructure.MatchOptInKey;
import com.unicompanion.learning.matching.infrastructure.MatchOptInRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final MatchOptInRepository optIns;

    public MatchingService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            MatchOptInRepository optIns
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.optIns = optIns;
    }

    @Transactional(readOnly = true)
    public MatchStatus status(UUID courseId, UUID userId) {
        requireEnrollment(courseId, userId);
        return toStatus(courseId, userId);
    }

    @Transactional
    public MatchStatus setOptIn(UUID courseId, UUID userId, boolean optedIn) {
        requireEnrollment(courseId, userId);
        MatchOptInKey key = new MatchOptInKey(courseId, userId);
        if (optedIn) {
            if (!optIns.existsById(key)) {
                optIns.save(MatchOptInEntity.create(key, Instant.now()));
                log.info("Match opt-in course={} user={}", courseId, userId);
            }
        } else if (optIns.existsById(key)) {
            optIns.deleteById(key);
            log.info("Match opt-out course={} user={}", courseId, userId);
        }
        return toStatus(courseId, userId);
    }

    private MatchStatus toStatus(UUID courseId, UUID userId) {
        boolean optedIn = optIns.existsById(new MatchOptInKey(courseId, userId));
        long activeCount = optIns.countByIdCourseId(courseId);
        return new MatchStatus(optedIn, activeCount);
    }

    private void requireEnrollment(UUID courseId, UUID userId) {
        CourseEntity course = courses.findById(courseId).orElseThrow(MatchingService::notFound);
        if (!course.isPublished()) {
            throw notFound();
        }
        if (!enrollments.existsById(new EnrollmentKey(courseId, userId))) {
            log.warn("Match denied: user={} not enrolled in course={}", userId, courseId);
            throw forbidden();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public record MatchStatus(boolean optedIn, long activeCount) {
    }
}

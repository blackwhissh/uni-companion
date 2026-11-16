package com.unicompanion.learning.study.application;

import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
import com.unicompanion.learning.study.infrastructure.FlashcardReviewEntity;
import com.unicompanion.learning.study.infrastructure.FlashcardReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class FlashcardReviewService {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final FlashcardReviewRepository reviews;

    public FlashcardReviewService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            FlashcardReviewRepository reviews
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.reviews = reviews;
    }

    @Transactional
    public void mark(UUID courseId, UUID userId, UUID cardId, String front, String status) {
        requireEnrolled(courseId, userId);
        String resolved = normalizeStatus(status);
        String cleanFront = front == null ? "" : front.trim();
        if (cleanFront.isEmpty() || cleanFront.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That card has no prompt to remember.");
        }
        FlashcardReviewEntity.Key key = new FlashcardReviewEntity.Key(userId, cardId);
        FlashcardReviewEntity existing = reviews.findById(key).orElse(null);
        Instant now = Instant.now();
        if (existing == null) {
            reviews.save(FlashcardReviewEntity.create(userId, cardId, courseId, cleanFront, resolved, now));
            return;
        }
        existing.revise(courseId, cleanFront, resolved, now);
        reviews.save(existing);
    }

    private static String normalizeStatus(String status) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mark the card Again or Got it.");
        }
        return switch (status.trim().toUpperCase()) {
            case FlashcardReviewEntity.AGAIN -> FlashcardReviewEntity.AGAIN;
            case FlashcardReviewEntity.KNOWN -> FlashcardReviewEntity.KNOWN;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mark the card Again or Got it.");
        };
    }

    private void requireEnrolled(UUID courseId, UUID userId) {
        CourseEntity course = courses.findById(courseId).orElseThrow(FlashcardReviewService::notFound);
        if (!course.isPublished() && !course.getOwnerId().equals(userId)) {
            throw notFound();
        }
        if (!enrollments.existsById(new EnrollmentKey(courseId, userId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join the course to review cards.");
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "That course isn’t available.");
    }
}

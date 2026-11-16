package com.unicompanion.learning.study.application;

import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
import com.unicompanion.learning.study.infrastructure.FlashcardReviewEntity;
import com.unicompanion.learning.study.infrastructure.FlashcardReviewRepository;
import com.unicompanion.learning.study.infrastructure.QuizAttemptEntity;
import com.unicompanion.learning.study.infrastructure.QuizAttemptItemEntity;
import com.unicompanion.learning.study.infrastructure.QuizAttemptItemRepository;
import com.unicompanion.learning.study.infrastructure.QuizAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProgressService {

    private static final Logger log = LoggerFactory.getLogger(ProgressService.class);

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final QuizAttemptRepository attempts;
    private final QuizAttemptItemRepository items;
    private final FlashcardReviewRepository cardReviews;

    public ProgressService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            QuizAttemptRepository attempts,
            QuizAttemptItemRepository items,
            FlashcardReviewRepository cardReviews
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.attempts = attempts;
        this.items = items;
        this.cardReviews = cardReviews;
    }

    @Transactional(readOnly = true)
    public ProgressSummary.Result view(UUID courseId, UUID userId) {
        requireEnrollment(courseId, userId);
        List<QuizAttemptEntity> stored = attempts.findForUserInCourse(userId, courseId);
        List<UUID> attemptIds = stored.stream().map(QuizAttemptEntity::getId).toList();
        Map<UUID, List<QuizAttemptItemEntity>> byAttempt = attemptIds.isEmpty()
                ? Map.of()
                : items.findByAttemptIdIn(attemptIds).stream()
                        .collect(Collectors.groupingBy(QuizAttemptItemEntity::getAttemptId));
        List<ProgressSummary.Attempt> records = new ArrayList<>();
        for (QuizAttemptEntity attempt : stored) {
            List<ProgressSummary.Item> attemptItems = byAttempt.getOrDefault(attempt.getId(), List.of()).stream()
                    .map(item -> new ProgressSummary.Item(item.getPrompt(), item.isCorrect(), item.getSortOrder()))
                    .toList();
            records.add(new ProgressSummary.Attempt(
                    attempt.getId(),
                    attempt.getScore(),
                    attempt.getTotal(),
                    attempt.getCreatedAt(),
                    attemptItems
            ));
        }
        ProgressSummary.Result quiz = ProgressSummary.summarize(records);
        List<FlashcardReviewEntity> again = cardReviews.findByCourseIdAndUserIdAndStatus(
                courseId,
                userId,
                FlashcardReviewEntity.AGAIN
        );
        Instant latestMark = again.stream()
                .map(FlashcardReviewEntity::getUpdatedAt)
                .max(java.util.Comparator.naturalOrder())
                .orElse(null);
        List<String> fronts = again.stream().map(FlashcardReviewEntity::getFront).toList();
        return new ProgressSummary.Result(
                quiz.attemptCount(),
                quiz.latestScore(),
                quiz.latestTotal(),
                quiz.bestScore(),
                quiz.bestTotal(),
                quiz.attempts(),
                CardGaps.merge(quiz.weakTopics(), fronts, latestMark)
        );
    }

    private void requireEnrollment(UUID courseId, UUID userId) {
        CourseEntity course = courses.findById(courseId).orElseThrow(ProgressService::notFound);
        boolean owner = course.getOwnerId().equals(userId);
        if (!course.isPublished() && !owner) {
            throw notFound();
        }
        if (!enrollments.existsById(new EnrollmentKey(courseId, userId))) {
            log.warn("Progress denied: user={} not enrolled in course={}", userId, courseId);
            throw forbidden();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}

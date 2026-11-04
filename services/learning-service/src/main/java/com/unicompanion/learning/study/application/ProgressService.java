package com.unicompanion.learning.study.application;

import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
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

    public ProgressService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            QuizAttemptRepository attempts,
            QuizAttemptItemRepository items
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.attempts = attempts;
        this.items = items;
    }

    @Transactional(readOnly = true)
    public ProgressSummary.Result view(UUID courseId, UUID userId) {
        requireEnrollment(courseId, userId);
        List<QuizAttemptEntity> stored = attempts.findForUserInCourse(userId, courseId);
        if (stored.isEmpty()) {
            return ProgressSummary.summarize(List.of());
        }
        List<UUID> attemptIds = stored.stream().map(QuizAttemptEntity::getId).toList();
        Map<UUID, List<QuizAttemptItemEntity>> byAttempt = items.findByAttemptIdIn(attemptIds).stream()
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
        return ProgressSummary.summarize(records);
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

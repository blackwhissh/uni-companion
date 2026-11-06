package com.unicompanion.learning.notes;

import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StudentNoteService {

    private static final Logger log = LoggerFactory.getLogger(StudentNoteService.class);

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final StudentNoteRepository notes;

    public StudentNoteService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            StudentNoteRepository notes
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public List<NoteView> list(UUID courseId, UUID userId) {
        requireEnrollment(courseId, userId);
        return notes.findByCourseIdAndUserIdOrderByUpdatedAtDesc(courseId, userId)
                .stream()
                .map(NoteView::from)
                .toList();
    }

    @Transactional
    public NoteView create(UUID courseId, UUID userId, String title, String body) {
        requireEnrollment(courseId, userId);
        String cleanTitle = cleanTitle(title);
        String cleanBody = cleanBody(body);
        StudentNoteEntity saved = notes.save(StudentNoteEntity.create(
                UUID.randomUUID(),
                courseId,
                userId,
                cleanTitle,
                cleanBody,
                Instant.now()
        ));
        log.info("Created private note id={} course={} user={}", saved.getId(), courseId, userId);
        return NoteView.from(saved);
    }

    @Transactional
    public NoteView update(UUID courseId, UUID noteId, UUID userId, String title, String body) {
        requireEnrollment(courseId, userId);
        StudentNoteEntity note = notes.findByIdAndCourseIdAndUserId(noteId, courseId, userId)
                .orElseThrow(StudentNoteService::notFound);
        note.update(cleanTitle(title), cleanBody(body), Instant.now());
        log.info("Updated private note id={} course={} user={}", noteId, courseId, userId);
        return NoteView.from(note);
    }

    @Transactional
    public void delete(UUID courseId, UUID noteId, UUID userId) {
        requireEnrollment(courseId, userId);
        StudentNoteEntity note = notes.findByIdAndCourseIdAndUserId(noteId, courseId, userId)
                .orElseThrow(StudentNoteService::notFound);
        notes.delete(note);
        log.info("Deleted private note id={} course={} user={}", noteId, courseId, userId);
    }

    @Transactional
    public void clearCourseNotes(UUID courseId, UUID userId) {
        notes.deleteByCourseIdAndUserId(courseId, userId);
    }

    private void requireEnrollment(UUID courseId, UUID userId) {
        CourseEntity course = courses.findById(courseId).orElseThrow(StudentNoteService::notFound);
        if (!course.isPublished() && !course.getOwnerId().equals(userId)) {
            throw notFound();
        }
        if (!enrollments.existsById(new EnrollmentKey(courseId, userId))) {
            throw forbidden();
        }
    }

    private static String cleanTitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty() || trimmed.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give your note a short title (up to 160 characters).");
        }
        return trimmed;
    }

    private static String cleanBody(String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty() || trimmed.length() > 8000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write a few lines for your note (up to 8000 characters).");
        }
        return trimmed;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "That note isn’t here anymore.");
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Join this course first to keep private notes here.");
    }

    public record NoteView(
            UUID id,
            UUID courseId,
            String title,
            String body,
            Instant createdAt,
            Instant updatedAt
    ) {
        static NoteView from(StudentNoteEntity entity) {
            return new NoteView(
                    entity.getId(),
                    entity.getCourseId(),
                    entity.getTitle(),
                    entity.getBody(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt()
            );
        }
    }
}

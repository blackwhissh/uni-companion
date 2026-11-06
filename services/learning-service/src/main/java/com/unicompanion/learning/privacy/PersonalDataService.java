package com.unicompanion.learning.privacy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PersonalDataService {

    private static final Logger log = LoggerFactory.getLogger(PersonalDataService.class);

    private final JdbcTemplate jdbc;

    public PersonalDataService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PersonalDataExport exportFor(UUID userId) {
        List<EnrolledCourseRecord> enrollments = jdbc.query(
                """
                select e.course_id, c.title, c.code, c.term, e.created_at
                from enrollments e
                join courses c on c.id = e.course_id
                where e.user_id = ?
                order by e.created_at desc
                """,
                (rs, rowNum) -> new EnrolledCourseRecord(
                        UUID.fromString(rs.getString("course_id")),
                        rs.getString("title"),
                        rs.getString("code"),
                        rs.getString("term"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                userId
        );

        List<StudyPlanRecord> studyPlans = jdbc.query(
                """
                select sp.course_id, c.code, sp.exam_date, sp.updated_at
                from study_plans sp
                join courses c on c.id = sp.course_id
                where sp.user_id = ?
                order by sp.exam_date asc
                """,
                (rs, rowNum) -> new StudyPlanRecord(
                        UUID.fromString(rs.getString("course_id")),
                        rs.getString("code"),
                        rs.getObject("exam_date", LocalDate.class),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                userId
        );

        List<QuizAttemptRecord> quizAttempts = jdbc.query(
                """
                select qa.id, q.course_id, qa.score, qa.total, qa.created_at
                from quiz_attempts qa
                join quizzes q on q.id = qa.quiz_id
                where qa.user_id = ?
                order by qa.created_at desc
                """,
                (rs, rowNum) -> new QuizAttemptRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("course_id")),
                        rs.getInt("score"),
                        rs.getInt("total"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                userId
        );

        List<MatchProfileRecord> matchProfiles = jdbc.query(
                """
                select m.course_id, c.code, m.display_name, m.contact_email, m.created_at
                from match_opt_ins m
                join courses c on c.id = m.course_id
                where m.user_id = ?
                order by m.created_at desc
                """,
                (rs, rowNum) -> {
                    UUID courseId = UUID.fromString(rs.getString("course_id"));
                    List<String> intents = jdbc.queryForList(
                            "select intent from match_intents where course_id = ? and user_id = ? order by intent asc",
                            String.class,
                            courseId,
                            userId
                    );
                    return new MatchProfileRecord(
                            courseId,
                            rs.getString("code"),
                            rs.getString("display_name"),
                            rs.getString("contact_email"),
                            intents,
                            rs.getTimestamp("created_at").toInstant()
                    );
                },
                userId
        );

        List<ContactRecord> contacts = jdbc.query(
                """
                select id, course_id, requester_id, requester_name, recipient_name, status, created_at
                from contact_requests
                where requester_id = ? or recipient_id = ?
                order by created_at desc
                """,
                (rs, rowNum) -> {
                    boolean outgoing = UUID.fromString(rs.getString("requester_id")).equals(userId);
                    return new ContactRecord(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("course_id")),
                            outgoing ? "OUTGOING" : "INCOMING",
                            outgoing ? rs.getString("recipient_name") : rs.getString("requester_name"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toInstant()
                    );
                },
                userId,
                userId
        );

        List<PrivateNoteRecord> privateNotes = jdbc.query(
                """
                select n.id, n.course_id, c.code, n.title, n.body, n.updated_at
                from student_notes n
                join courses c on c.id = n.course_id
                where n.user_id = ?
                order by n.updated_at desc
                """,
                (rs, rowNum) -> new PrivateNoteRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("course_id")),
                        rs.getString("code"),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                userId
        );

        return new PersonalDataExport(
                userId,
                Instant.now(),
                enrollments,
                studyPlans,
                quizAttempts,
                matchProfiles,
                contacts,
                privateNotes
        );
    }

    @Transactional
    public void purgeFor(UUID userId) {
        jdbc.update("delete from contact_requests where requester_id = ? or recipient_id = ?", userId, userId);
        jdbc.update("delete from match_opt_ins where user_id = ?", userId);
        jdbc.update("delete from student_notes where user_id = ?", userId);
        jdbc.update("delete from study_plans where user_id = ?", userId);
        jdbc.update("delete from quiz_attempts where user_id = ?", userId);
        jdbc.update("delete from quiz_progress where user_id = ?", userId);
        jdbc.update("delete from flashcard_deck_progress where user_id = ?", userId);
        jdbc.update("delete from study_delivery_events where user_id = ?", userId);
        jdbc.update("delete from study_version_reports where user_id = ?", userId);
        jdbc.update("delete from enrollments where user_id = ?", userId);
        log.info("Purged learning personal data for user={}", userId);
    }

    public record PersonalDataExport(
            UUID userId,
            Instant exportedAt,
            List<EnrolledCourseRecord> enrollments,
            List<StudyPlanRecord> studyPlans,
            List<QuizAttemptRecord> quizAttempts,
            List<MatchProfileRecord> matchProfiles,
            List<ContactRecord> contacts,
            List<PrivateNoteRecord> privateNotes
    ) {
    }

    public record EnrolledCourseRecord(
            UUID courseId,
            String title,
            String code,
            String term,
            Instant enrolledAt
    ) {
    }

    public record StudyPlanRecord(
            UUID courseId,
            String courseCode,
            LocalDate examDate,
            Instant updatedAt
    ) {
    }

    public record QuizAttemptRecord(
            UUID attemptId,
            UUID courseId,
            int score,
            int total,
            Instant createdAt
    ) {
    }

    public record MatchProfileRecord(
            UUID courseId,
            String courseCode,
            String displayName,
            String contactEmail,
            List<String> intents,
            Instant optedInAt
    ) {
    }

    public record ContactRecord(
            UUID requestId,
            UUID courseId,
            String direction,
            String peerDisplayName,
            String status,
            Instant createdAt
    ) {
    }

    public record PrivateNoteRecord(
            UUID noteId,
            UUID courseId,
            String courseCode,
            String title,
            String body,
            Instant updatedAt
    ) {
    }
}

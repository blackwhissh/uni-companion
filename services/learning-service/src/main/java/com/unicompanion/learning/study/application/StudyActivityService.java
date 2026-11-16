package com.unicompanion.learning.study.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StudyActivityService {

    public static final String CHAT = "CHAT";
    public static final String QUIZ = "QUIZ";
    public static final String CARDS = "CARDS";
    public static final String SUMMARY = "SUMMARY";

    private final JdbcTemplate jdbc;

    public StudyActivityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(UUID userId, UUID courseId, List<UUID> materialIds, String kind) {
        if (userId == null || courseId == null || materialIds == null || kind == null || kind.isBlank()) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        for (UUID materialId : materialIds.stream().distinct().toList()) {
            if (materialId == null) {
                continue;
            }
            jdbc.update(
                    """
                    INSERT INTO study_activity (user_id, course_id, material_id, kind, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (user_id, material_id, kind)
                    DO UPDATE SET updated_at = EXCLUDED.updated_at, course_id = EXCLUDED.course_id
                    """,
                    userId,
                    courseId,
                    materialId,
                    kind,
                    now
            );
        }
    }

    @Transactional
    public void recordQuiz(UUID userId, UUID quizId) {
        if (userId == null || quizId == null) {
            return;
        }
        List<UUID> courseIds = jdbc.query(
                "SELECT course_id FROM quizzes WHERE id = ?",
                (rs, rowNum) -> rs.getObject("course_id", UUID.class),
                quizId
        );
        if (courseIds.isEmpty()) {
            return;
        }
        List<UUID> materialIds = jdbc.query(
                "SELECT material_id FROM quiz_materials WHERE quiz_id = ?",
                (rs, rowNum) -> rs.getObject("material_id", UUID.class),
                quizId
        );
        record(userId, courseIds.getFirst(), materialIds, QUIZ);
    }

    @Transactional(readOnly = true)
    public List<MaterialActivity> forCourse(UUID courseId, UUID userId) {
        Map<UUID, List<String>> kinds = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT material_id, kind
                FROM study_activity
                WHERE course_id = ? AND user_id = ?
                ORDER BY material_id, kind
                """,
                rs -> {
                    UUID materialId = rs.getObject("material_id", UUID.class);
                    kinds.computeIfAbsent(materialId, ignored -> new ArrayList<>()).add(rs.getString("kind"));
                },
                courseId,
                userId
        );
        return kinds.entrySet().stream()
                .map(entry -> new MaterialActivity(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    public record MaterialActivity(UUID materialId, List<String> kinds) {
    }
}

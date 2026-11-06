package com.unicompanion.learning.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OpenApiController {

    @GetMapping(path = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> apiDocs() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("openapi", "3.1.0");
        doc.put("info", Map.of(
                "title", "Uni Companion Learning Service API",
                "version", "1.0.0",
                "description", "Courses, canonical PDF materials, grounded RAG Q&A/search/summaries, flashcards, quizzes, study plans, matching, private student notes, and GDPR personal data export."
        ));
        doc.put("components", Map.of(
                "securitySchemes", Map.of(
                        "bearerAuth", Map.of("type", "http", "scheme", "bearer", "bearerFormat", "JWT")
                )
        ));
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/api/courses", Map.of(
                "get", operation("List courses visible to the caller"),
                "post", operation("Create a course (COURSE_ADMIN / ADMIN)")
        ));
        paths.put("/api/courses/{id}", Map.of(
                "get", operation("Get course details"),
                "delete", operation("Delete a course")
        ));
        paths.put("/api/courses/{id}/visibility", Map.of("patch", operation("Publish or unpublish a course")));
        paths.put("/api/courses/{id}/owner", Map.of("patch", operation("Transfer course ownership (UC-052)")));
        paths.put("/api/courses/{id}/enroll", Map.of(
                "post", operation("Enroll in a published course"),
                "delete", operation("Unenroll from a course")
        ));
        paths.put("/api/courses/{courseId}/materials", Map.of(
                "get", operation("List materials for a course"),
                "post", operation("Upload a canonical PDF material")
        ));
        paths.put("/api/materials/{id}/file", Map.of(
                "get", operation("Download PDF material"),
                "post", operation("Replace PDF material and re-index (UC-016)")
        ));
        paths.put("/api/materials/{id}/retry", Map.of("post", operation("Retry indexing a failed PDF (NFR-010)")));
        paths.put("/api/materials/{id}/visibility", Map.of("patch", operation("Publish or unpublish a material")));
        paths.put("/api/rag/query", Map.of("post", operation("Grounded Q&A over published course materials (EN/DE)")));
        paths.put("/api/rag/search", Map.of("post", operation("Semantic search across published course materials")));
        paths.put("/api/rag/summary", Map.of("post", operation("Summarize a published lecture (EN/DE)")));
        paths.put("/api/study/courses/{courseId}/flashcards", Map.of("get", operation("Get active flashcard deck")));
        paths.put("/api/study/courses/{courseId}/flashcards/generate", Map.of("post", operation("Generate or reuse flashcard deck")));
        paths.put("/api/study/courses/{courseId}/quizzes", Map.of("get", operation("Get active practice quiz")));
        paths.put("/api/study/courses/{courseId}/quizzes/generate", Map.of("post", operation("Generate or reuse practice quiz")));
        paths.put("/api/study/quizzes/{quizId}/submit", Map.of("post", operation("Submit quiz answers and score attempt")));
        paths.put("/api/study/courses/{courseId}/progress", Map.of("get", operation("Get quiz progress and weak topics")));
        paths.put("/api/study/courses/{courseId}/plan", Map.of(
                "get", operation("Get exam study plan"),
                "put", operation("Set exam date and compute study plan")
        ));
        paths.put("/api/match/courses/{courseId}/active-count", Map.of("get", operation("Get opted-in study partner count")));
        paths.put("/api/match/courses/{courseId}/opt-in", Map.of("put", operation("Opt in or out of study-partner matching")));
        paths.put("/api/match/courses/{courseId}/intents", Map.of("put", operation("Update collaboration intents")));
        paths.put("/api/match/courses/{courseId}/suggestions", Map.of("get", operation("Get study-partner suggestions and requests")));
        paths.put("/api/match/courses/{courseId}/contacts", Map.of("post", operation("Send a contact request")));
        paths.put("/api/match/courses/{courseId}/contacts/{requestId}/accept", Map.of("post", operation("Accept a contact request and share email")));
        paths.put("/api/match/courses/{courseId}/contacts/{requestId}/decline", Map.of("post", operation("Decline a contact request")));
        paths.put("/api/match/courses/{courseId}/contacts/{requestId}", Map.of("delete", operation("Cancel or disconnect a contact")));
        paths.put("/api/notes/courses/{courseId}", Map.of(
                "get", operation("List private student notes for a course (UC-017)"),
                "post", operation("Create a private student note (UC-017)")
        ));
        paths.put("/api/notes/courses/{courseId}/{noteId}", Map.of(
                "put", operation("Update a private student note (UC-017)"),
                "delete", operation("Delete a private student note (UC-017)")
        ));
        paths.put("/api/me/data", Map.of(
                "get", operation("Export personal learning data as JSON (NFR-004)"),
                "delete", operation("Purge personal learning data (NFR-004)")
        ));
        paths.put("/api/admin/courses", Map.of("get", operation("List courses with enrollment and material counts for platform admin")));
        doc.put("paths", paths);
        return doc;
    }

    private static Map<String, Object> operation(String summary) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("summary", summary);
        op.put("security", List.of(Map.of("bearerAuth", List.of())));
        op.put("responses", Map.of("200", Map.of("description", "OK")));
        return op;
    }
}

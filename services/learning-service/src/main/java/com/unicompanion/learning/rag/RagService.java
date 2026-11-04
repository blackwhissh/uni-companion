package com.unicompanion.learning.rag;

import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
import com.unicompanion.learning.ingestion.EmbeddingException;
import com.unicompanion.learning.ingestion.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int TOP_K = 4;
    private static final int SEARCH_LIMIT = 8;
    private static final int SEARCH_QUERY_MAX = 200;

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final EmbeddingModel embeddings;
    private final ChunkRetrievalRepository chunks;
    private final ChatModel chat;

    public RagService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            EmbeddingModel embeddings,
            ChunkRetrievalRepository chunks,
            ChatModel chat
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.chat = chat;
    }

    public Answer query(UUID courseId, UUID userId, String question, List<UUID> materialIds, String language) {
        String trimmed = question == null ? "" : question.trim();
        if (trimmed.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ask a question.");
        }
        if (trimmed.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question must be at most 2000 characters.");
        }
        ExplanationLanguage resolvedLanguage = resolveLanguage(language, trimmed);
        CourseEntity course = courses.findById(courseId).orElseThrow(RagService::notFound);
        boolean owner = course.getOwnerId().equals(userId);
        if (!course.isPublished() && !owner) {
            throw notFound();
        }
        if (!enrollments.existsById(new EnrollmentKey(courseId, userId))) {
            log.warn("RAG denied: user={} not enrolled in course={}", userId, courseId);
            throw forbidden();
        }
        if (materialIds == null || materialIds.isEmpty() || materialIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one material.");
        }
        int selectedCount = (int) materialIds.stream().distinct().count();
        if (chunks.countPublishedReadyMaterials(courseId, materialIds) != selectedCount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Every selected material must be ready, published, and part of this course."
            );
        }
        float[] queryEmbedding;
        List<ChatModel.RetrievedChunk> retrieved;
        String rawAnswer;
        try {
            queryEmbedding = embeddings.embed(trimmed);
            retrieved = chunks.findTopK(courseId, materialIds, queryEmbedding, TOP_K);
            rawAnswer = chat.answer(trimmed, retrieved, resolvedLanguage);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (EmbeddingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not draft an answer from the published materials. Try again shortly.",
                    ex
            );
        }
        CitedSources.Result grounded = CitedSources.select(rawAnswer, retrieved);
        log.info(
                "RAG query course={} user={} materials={} language={} questionChars={} retrieved={} cited={} answerChars={}",
                courseId,
                userId,
                selectedCount,
                resolvedLanguage,
                trimmed.length(),
                retrieved.size(),
                grounded.chunks().size(),
                grounded.answer().length()
        );
        return new Answer(
                grounded.answer(),
                grounded.chunks().stream()
                        .map(chunk -> new Citation(
                                chunk.materialId(),
                                chunk.title(),
                                CitationText.sectionHint(chunk.content()),
                                chunk.pageNumber(),
                                CitationText.excerpt(chunk.content(), trimmed, grounded.answer())
                        ))
                        .toList()
        );
    }

    public SearchResult search(UUID courseId, UUID userId, String query, List<UUID> materialIds) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a search.");
        }
        if (trimmed.length() > SEARCH_QUERY_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search must be at most 200 characters.");
        }
        requireEnrolled(courseId, userId);
        List<UUID> scope = resolveSearchScope(courseId, materialIds);
        if (scope.isEmpty()) {
            return new SearchResult(List.of());
        }
        List<ChatModel.RetrievedChunk> retrieved;
        try {
            retrieved = chunks.findTopK(courseId, scope, embeddings.embed(trimmed), SEARCH_LIMIT);
        } catch (EmbeddingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
        log.info(
                "RAG search course={} user={} materials={} queryChars={} hits={}",
                courseId,
                userId,
                scope.size(),
                trimmed.length(),
                retrieved.size()
        );
        return new SearchResult(retrieved.stream()
                .map(chunk -> new SearchHit(
                        chunk.materialId(),
                        chunk.title(),
                        chunk.pageNumber(),
                        CitationText.excerpt(chunk.content(), trimmed, "")
                ))
                .toList());
    }

    public Answer summarize(UUID courseId, UUID userId, UUID materialId, String language) {
        requireEnrolled(courseId, userId);
        ExplanationLanguage resolvedLanguage = resolveLanguage(language, null);
        if (materialId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a lecture.");
        }
        if (chunks.countPublishedReadyMaterials(courseId, List.of(materialId)) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Every selected material must be ready, published, and part of this course."
            );
        }
        List<ChatModel.RetrievedChunk> pages = chunks.representativePublished(courseId, List.of(materialId), 6, 4000);
        if (pages.isEmpty()) {
            return new Answer("This lecture has no published text to summarize.", List.of());
        }
        String title = pages.getFirst().title();
        String raw;
        try {
            raw = chat.summarize(title, pages, resolvedLanguage);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not summarize this lecture. Try again shortly.",
                    ex
            );
        }
        CitedSources.Result grounded = CitedSources.select(raw, pages);
        log.info(
                "RAG summary course={} user={} material={} language={} pages={} cited={} answerChars={}",
                courseId,
                userId,
                materialId,
                resolvedLanguage,
                pages.size(),
                grounded.chunks().size(),
                grounded.answer().length()
        );
        return new Answer(
                grounded.answer(),
                grounded.chunks().stream()
                        .map(chunk -> new Citation(
                                chunk.materialId(),
                                chunk.title(),
                                CitationText.sectionHint(chunk.content()),
                                chunk.pageNumber(),
                                CitationText.excerpt(chunk.content(), title, grounded.answer())
                        ))
                        .toList()
        );
    }

    private static ExplanationLanguage resolveLanguage(String raw, String question) {
        if (raw != null && !raw.isBlank()) {
            return switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "EN" -> ExplanationLanguage.EN;
                case "DE" -> ExplanationLanguage.DE;
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose English or German.");
            };
        }
        if (question != null && QuestionLanguage.isLikelyGerman(question)) {
            return ExplanationLanguage.DE;
        }
        return ExplanationLanguage.EN;
    }

    private void requireEnrolled(UUID courseId, UUID userId) {
        CourseEntity course = courses.findById(courseId).orElseThrow(RagService::notFound);
        boolean owner = course.getOwnerId().equals(userId);
        if (!course.isPublished() && !owner) {
            throw notFound();
        }
        if (!enrollments.existsById(new EnrollmentKey(courseId, userId))) {
            log.warn("RAG denied: user={} not enrolled in course={}", userId, courseId);
            throw forbidden();
        }
    }

    private List<UUID> resolveSearchScope(UUID courseId, List<UUID> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return chunks.publishedReadyMaterialIds(courseId);
        }
        if (materialIds.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one material.");
        }
        int selectedCount = (int) materialIds.stream().distinct().count();
        if (chunks.countPublishedReadyMaterials(courseId, materialIds) != selectedCount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Every selected material must be ready, published, and part of this course."
            );
        }
        return materialIds.stream().distinct().toList();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public record Answer(String answer, List<Citation> citations) {
    }

    public record Citation(
            UUID materialId,
            String title,
            String sectionHint,
            int pageNumber,
            String excerpt
    ) {
    }

    public record SearchResult(List<SearchHit> hits) {
    }

    public record SearchHit(UUID materialId, String title, int pageNumber, String excerpt) {
    }
}

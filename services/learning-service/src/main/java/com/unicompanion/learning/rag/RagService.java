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
import java.util.UUID;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int TOP_K = 4;

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

    public Answer query(UUID courseId, UUID userId, String question, List<UUID> materialIds) {
        String trimmed = question == null ? "" : question.trim();
        if (trimmed.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ask a question.");
        }
        if (trimmed.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question must be at most 2000 characters.");
        }
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
            rawAnswer = chat.answer(trimmed, retrieved);
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
                "RAG query course={} user={} materials={} questionChars={} retrieved={} cited={} answerChars={}",
                courseId,
                userId,
                selectedCount,
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
}

package com.unicompanion.learning.rag;

import com.unicompanion.learning.config.RagProperties;
import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
import com.unicompanion.learning.ingestion.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int HISTORY_DEFAULT = 40;
    private static final int HISTORY_MAX = 100;
    static final String MODEL_UNAVAILABLE = """
            The language model is unavailable.
            Retrieved passages from the published materials are listed with this reply. Nothing here was written by the model.
            """;

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final EmbeddingModel embeddings;
    private final ChunkRetrievalRepository chunks;
    private final ChatModel chat;
    private final RagRateLimiter rateLimiter;
    private final QaTurnRepository qaTurns;
    private final Environment environment;
    private final int topK;
    private final double maxCosineDistance;
    private final boolean allowModelDownHeader;

    public RagService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            EmbeddingModel embeddings,
            ChunkRetrievalRepository chunks,
            ChatModel chat,
            RagProperties ragProperties,
            RagRateLimiter rateLimiter,
            QaTurnRepository qaTurns,
            Environment environment
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.chat = chat;
        this.rateLimiter = rateLimiter;
        this.qaTurns = qaTurns;
        this.environment = environment;
        this.topK = ragProperties.topK();
        this.maxCosineDistance = ragProperties.maxCosineDistance();
        this.allowModelDownHeader = ragProperties.allowModelDownHeader();
    }

    public Answer query(UUID courseId, UUID userId, String question, List<UUID> materialIds) {
        return query(courseId, userId, question, materialIds, null, null, false);
    }

    public Answer query(
            UUID courseId,
            UUID userId,
            String question,
            List<UUID> materialIds,
            String priorQuestion,
            String priorAnswer
    ) {
        return query(courseId, userId, question, materialIds, priorQuestion, priorAnswer, false);
    }

    public Answer query(
            UUID courseId,
            UUID userId,
            String question,
            List<UUID> materialIds,
            String priorQuestion,
            String priorAnswer,
            boolean forceModelDown
    ) {
        rateLimiter.check(userId);
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
        boolean ownerPreview = owner;
        if (chunks.countReadyMaterials(courseId, materialIds, ownerPreview) != selectedCount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ownerPreview
                            ? "Every selected material must be ready and part of this course."
                            : "Every selected material must be ready, published, and part of this course."
            );
        }
        long started = System.nanoTime();
        int embedLatencyMs = 0;
        int answerLatencyMs = 0;
        boolean embedFailed = false;
        List<ChatModel.RetrievedChunk> candidates = List.of();
        try {
            long embedStart = System.nanoTime();
            float[] queryEmbedding = embeddings.embed(trimmed);
            embedLatencyMs = elapsedMs(embedStart);
            candidates = chunks.findTopK(
                    courseId, materialIds, queryEmbedding, topK, ownerPreview, trimmed, maxCosineDistance
            );
        } catch (RuntimeException ex) {
            embedFailed = true;
            embedLatencyMs = elapsedMs(started);
            log.warn("Embed failed for course={} user={}: {}", courseId, userId, ex.toString());
            try {
                candidates = chunks.findLexicalOnly(courseId, materialIds, trimmed, topK, ownerPreview);
            } catch (RuntimeException lexicalEx) {
                log.warn("Lexical retrieval also failed for course={}: {}", courseId, lexicalEx.toString());
                candidates = List.of();
            }
        }
        boolean modelDown = embedFailed || (forceModelDown && allowModelDownHeader);
        List<ChatModel.RetrievedChunk> supported = selectPages(
                courseId, materialIds, ownerPreview, trimmed, candidates, modelDown
        );
        String rawAnswer;
        if (modelDown) {
            rawAnswer = MODEL_UNAVAILABLE;
        } else {
            long answerStart = System.nanoTime();
            try {
                rawAnswer = chat.answer(trimmed, supported, sanitizePrior(priorQuestion), sanitizePrior(priorAnswer));
                answerLatencyMs = elapsedMs(answerStart);
            } catch (RuntimeException ex) {
                modelDown = true;
                answerLatencyMs = elapsedMs(answerStart);
                rawAnswer = MODEL_UNAVAILABLE;
                log.warn("Chat model failed for course={} user={}: {}", courseId, userId, ex.toString());
            }
        }
        boolean modelAvailable = !modelDown && !environment.acceptsProfiles(Profiles.of("fake"));
        CitedSources.Result grounded = modelDown
                ? new CitedSources.Result(rawAnswer, List.of())
                : CitedSources.select(rawAnswer, supported);
        List<ChatModel.RetrievedChunk> citedChunks = grounded.chunks();
        List<Citation> citations = toCitations(citedChunks, trimmed, grounded.answer());
        List<Citation> passages = toCitations(
                modelDown ? candidates : supported,
                trimmed,
                grounded.answer()
        );
        boolean refused = !modelDown && citations.isEmpty();
        Answer answer = new Answer(grounded.answer(), List.copyOf(citations));
        int latencyMs = Math.min(Integer.MAX_VALUE, embedLatencyMs + answerLatencyMs);
        boolean followUp = priorQuestion != null && !priorQuestion.isBlank();
        persistTurn(
                courseId,
                userId,
                trimmed,
                answer,
                materialIds,
                candidates.size(),
                citations.size(),
                refused,
                latencyMs,
                embedLatencyMs,
                answerLatencyMs,
                modelAvailable,
                passages,
                followUp
        );
        log.info(
                "RAG query course={} user={} materials={} questionChars={} retrieved={} cited={} refused={} modelAvailable={} embedMs={} answerMs={} answerChars={} followUp={}",
                courseId,
                userId,
                selectedCount,
                trimmed.length(),
                candidates.size(),
                citations.size(),
                refused,
                modelAvailable,
                embedLatencyMs,
                answerLatencyMs,
                grounded.answer().length(),
                followUp
        );
        return answer;
    }

    public List<HistoryTurn> history(UUID courseId, UUID userId, Integer limit) {
        requireEnrolledReader(courseId, userId);
        int pageSize = clampHistoryLimit(limit);
        List<QaTurnEntity> newestFirst = qaTurns.findByCourseIdAndUserIdOrderByCreatedAtDesc(
                courseId,
                userId,
                PageRequest.of(0, pageSize)
        );
        List<HistoryTurn> chronological = new ArrayList<>(newestFirst.size());
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            chronological.add(toHistoryTurn(newestFirst.get(i)));
        }
        return List.copyOf(chronological);
    }

    public CourseStats stats(UUID courseId, UUID userId) {
        CourseEntity course = courses.findById(courseId).orElseThrow(RagService::notFound);
        if (!course.getOwnerId().equals(userId)) {
            throw forbidden();
        }
        long total = qaTurns.countByCourseId(courseId);
        if (total == 0) {
            return new CourseStats(0, 0, 0, 0);
        }
        return new CourseStats(
                total,
                (int) Math.round(qaTurns.averageLatencyMs(courseId)),
                qaTurns.citeRate(courseId),
                qaTurns.insufficientRate(courseId)
        );
    }

    private List<ChatModel.RetrievedChunk> selectPages(
            UUID courseId,
            List<UUID> materialIds,
            boolean ownerPreview,
            String question,
            List<ChatModel.RetrievedChunk> candidates,
            boolean modelDown
    ) {
        if (!modelDown && BroadStudyQuestion.matches(question)) {
            try {
                List<ChatModel.RetrievedChunk> pages = chunks.listReadyPages(courseId, materialIds, ownerPreview);
                List<ChatModel.RetrievedChunk> topicPages = TopicSelection.forBroadQuestion(question, pages, candidates);
                if (!topicPages.isEmpty()) {
                    log.info(
                            "RAG topic course={} pages={} selected={}",
                            courseId,
                            pages.size(),
                            topicPages.size()
                    );
                    return topicPages;
                }
            } catch (RuntimeException ex) {
                log.warn("Topic selection failed for course={}: {}", courseId, ex.toString());
            }
        }
        return PassageSupport.keep(question, candidates);
    }

    private void persistTurn(
            UUID courseId,
            UUID userId,
            String question,
            Answer answer,
            List<UUID> materialIds,
            int retrievedCount,
            int citedCount,
            boolean insufficient,
            int latencyMs,
            int embedLatencyMs,
            int answerLatencyMs,
            boolean modelAvailable,
            List<Citation> passages,
            boolean followUp
    ) {
        try {
            qaTurns.save(QaTurnEntity.create(
                    UUID.randomUUID(),
                    courseId,
                    userId,
                    question,
                    answer.answer() == null ? "" : answer.answer(),
                    QaTurnJson.citations(answer.citations()),
                    QaTurnJson.materialIds(materialIds.stream().distinct().toList()),
                    retrievedCount,
                    citedCount,
                    insufficient,
                    latencyMs,
                    embedLatencyMs,
                    answerLatencyMs,
                    modelAvailable,
                    QaTurnJson.citations(passages),
                    followUp,
                    Instant.now()
            ));
        } catch (RuntimeException ex) {
            log.warn("Failed to persist Q&A turn course={} user={}: {}", courseId, userId, ex.toString());
        }
    }

    private HistoryTurn toHistoryTurn(QaTurnEntity entity) {
        List<Citation> citations;
        try {
            citations = QaTurnJson.parseCitations(entity.getCitationsJson());
        } catch (RuntimeException ex) {
            log.warn("Could not parse stored citations_json: {}", ex.toString());
            citations = List.of();
        }
        return new HistoryTurn(
                entity.getId(),
                entity.getQuestion(),
                entity.getAnswer(),
                citations,
                entity.isInsufficient(),
                entity.getLatencyMs(),
                entity.getCitedCount(),
                entity.isFollowUp(),
                entity.getCreatedAt()
        );
    }

    private void requireEnrolledReader(UUID courseId, UUID userId) {
        CourseEntity course = courses.findById(courseId).orElseThrow(RagService::notFound);
        boolean owner = course.getOwnerId().equals(userId);
        if (!course.isPublished() && !owner) {
            throw notFound();
        }
        if (!enrollments.existsById(new EnrollmentKey(courseId, userId))) {
            throw forbidden();
        }
    }

    private static int clampHistoryLimit(Integer limit) {
        if (limit == null) {
            return HISTORY_DEFAULT;
        }
        return Math.max(1, Math.min(HISTORY_MAX, limit));
    }

    private static int elapsedMs(long startedNanos) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static List<Citation> toCitations(
            List<ChatModel.RetrievedChunk> chunks,
            String question,
            String answer
    ) {
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ChatModel.RetrievedChunk chunk = chunks.get(i);
            citations.add(new Citation(
                    chunk.materialId(),
                    chunk.title(),
                    CitationText.sectionHint(chunk.content()),
                    chunk.pageNumber(),
                    CitationText.excerpt(chunk.content(), question, answer, i + 1)
            ));
        }
        return citations;
    }

    private static String sanitizePrior(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > 4000 ? trimmed.substring(0, 4000) : trimmed;
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

    public record HistoryTurn(
            UUID id,
            String question,
            String answer,
            List<Citation> citations,
            boolean insufficient,
            int latencyMs,
            int citedCount,
            boolean followUp,
            Instant createdAt
    ) {
    }

    public record CourseStats(long totalTurns, int avgLatencyMs, double citeRate, double insufficientRate) {
    }
}

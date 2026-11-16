package com.unicompanion.learning.rag;

import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
import com.unicompanion.learning.config.ObservabilitySupport;
import com.unicompanion.learning.ingestion.EmbeddingException;
import com.unicompanion.learning.ingestion.EmbeddingModel;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class StudyChatService {

    private static final Logger log = LoggerFactory.getLogger(StudyChatService.class);
    private static final int TOP_K = 4;
    private static final int MAX_TURNS = 24;
    private static final int HISTORY_WINDOW = 10;

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final StudyChatSessionRepository sessions;
    private final StudyChatMessageRepository messages;
    private final EmbeddingModel embeddings;
    private final ChunkRetrievalRepository chunks;
    private final ChatModel chat;
    private final ObservationRegistry observations;
    private final TransactionTemplate transactions;

    public StudyChatService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            StudyChatSessionRepository sessions,
            StudyChatMessageRepository messages,
            EmbeddingModel embeddings,
            ChunkRetrievalRepository chunks,
            ChatModel chat,
            ObservationRegistry observations,
            PlatformTransactionManager transactionManager
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.sessions = sessions;
        this.messages = messages;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.chat = chat;
        this.observations = observations;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public SessionView start(UUID courseId, UUID userId, List<UUID> materialIds, String language) {
        requireEnrolled(courseId, userId);
        List<UUID> selected = normalizeMaterials(courseId, materialIds);
        ExplanationLanguage resolved = resolveLanguage(language, null);
        Instant now = Instant.now();
        StudyChatSessionEntity session = StudyChatSessionEntity.create(
                UUID.randomUUID(),
                courseId,
                userId,
                selected,
                resolved.name(),
                now
        );
        sessions.save(session);
        log.info("Study chat started session={} course={} user={} materials={}", session.getId(), courseId, userId, selected.size());
        return toView(session, List.of());
    }

    @Transactional(readOnly = true)
    public SessionView get(UUID sessionId, UUID userId) {
        StudyChatSessionEntity session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(StudyChatService::notFound);
        requireEnrolled(session.getCourseId(), userId);
        return toView(session, messages.findBySessionIdOrderByCreatedAtAsc(sessionId));
    }

    @Transactional
    public SessionView ask(UUID sessionId, UUID userId, String question) {
        Observation observation = ObservabilitySupport.start(observations, "learning.rag.chat.message");
        try {
            StringBuilder answer = new StringBuilder();
            return askInternal(sessionId, userId, question, observation, answer::append);
        } catch (RuntimeException ex) {
            observation.error(ex);
            throw ex;
        } finally {
            observation.stop();
        }
    }

    /**
     * Stream an assistant reply over SSE-friendly callbacks. Persistence uses short transactions
     * so the stream is not held inside one long DB transaction.
     */
    public void askStreaming(UUID sessionId, UUID userId, String question, StreamListener listener) {
        Observation observation = ObservabilitySupport.start(observations, "learning.rag.chat.message.stream");
        try {
            StringBuilder answer = new StringBuilder();
            SessionView view = askInternal(sessionId, userId, question, observation, delta -> {
                answer.append(delta);
                listener.onToken(delta);
            });
            listener.onDone(view);
        } catch (ResponseStatusException ex) {
            observation.error(ex);
            listener.onError(ex.getReason() == null ? "Could not answer that." : ex.getReason(), ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            observation.error(ex);
            listener.onError("Could not draft an answer from the published materials. Try again shortly.", 502);
        } finally {
            observation.stop();
        }
    }

    private SessionView askInternal(
            UUID sessionId,
            UUID userId,
            String question,
            Observation observation,
            Consumer<String> onDelta
    ) {
        PreparedAsk prepared = transactions.execute(status -> prepareAsk(sessionId, userId, question, observation));
        if (prepared == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not prepare the study chat.");
        }

        List<ChatModel.RetrievedChunk> retrieved;
        StringBuilder rawAnswer = new StringBuilder();
        try {
            float[] queryEmbedding = embeddings.embed(prepared.question());
            retrieved = chunks.findTopK(prepared.courseId(), prepared.materialIds(), queryEmbedding, TOP_K);
            chat.streamAnswerWithHistory(
                    prepared.question(),
                    retrieved,
                    prepared.language(),
                    prepared.history(),
                    delta -> {
                        if (delta != null && !delta.isEmpty()) {
                            rawAnswer.append(delta);
                            onDelta.accept(delta);
                        }
                    }
            );
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

        String fullAnswer = rawAnswer.toString();
        List<ChatModel.RetrievedChunk> retrievedChunks = retrieved;
        SessionView saved = transactions.execute(status ->
                persistAssistant(prepared, fullAnswer, retrievedChunks, observation)
        );
        if (saved == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the study chat reply.");
        }
        return saved;
    }

    private PreparedAsk prepareAsk(UUID sessionId, UUID userId, String question, Observation observation) {
        String trimmed = question == null ? "" : question.trim();
        if (trimmed.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ask a question about the lectures.");
        }
        if (trimmed.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep your message to 2000 characters.");
        }
        StudyChatSessionEntity session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(StudyChatService::notFound);
        requireEnrolled(session.getCourseId(), userId);
        List<UUID> materialIds = session.getMaterialIds();
        if (chunks.countPublishedReadyMaterials(session.getCourseId(), materialIds) != materialIds.stream().distinct().count()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Every selected material must be ready, published, and part of this course."
            );
        }
        if (messages.countBySessionId(sessionId) >= MAX_TURNS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This chat is full. Start a new chat to keep studying."
            );
        }

        Instant now = Instant.now();
        StudyChatMessageEntity userMessage = StudyChatMessageEntity.user(UUID.randomUUID(), sessionId, trimmed, now);
        messages.save(userMessage);

        List<StudyChatMessageEntity> prior = messages.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<ChatModel.Turn> history = prior.stream()
                .filter(message -> !message.getId().equals(userMessage.getId()))
                .map(message -> new ChatModel.Turn(message.getRole(), message.getContent()))
                .toList();
        if (history.size() > HISTORY_WINDOW) {
            history = history.subList(history.size() - HISTORY_WINDOW, history.size());
        }
        observation.highCardinalityKeyValue("history.turns", String.valueOf(history.size()));

        ExplanationLanguage language = resolveLanguage(session.getLanguage(), trimmed);
        observation.lowCardinalityKeyValue("language", language.name());
        return new PreparedAsk(
                session.getId(),
                session.getCourseId(),
                userId,
                materialIds,
                trimmed,
                language,
                history,
                userMessage.getId()
        );
    }

    private SessionView persistAssistant(
            PreparedAsk prepared,
            String rawAnswer,
            List<ChatModel.RetrievedChunk> retrieved,
            Observation observation
    ) {
        CitedSources.Result grounded = CitedSources.select(rawAnswer, retrieved);
        List<StudyChatMessageEntity.StoredCitation> citations = grounded.chunks().stream()
                .map(chunk -> new StudyChatMessageEntity.StoredCitation(
                        chunk.materialId(),
                        chunk.title(),
                        CitationText.sectionHint(chunk.content()),
                        chunk.pageNumber(),
                        CitationText.excerpt(chunk.content(), prepared.question(), grounded.answer())
                ))
                .toList();
        observation.lowCardinalityKeyValue("cited", String.valueOf(!citations.isEmpty()));

        StudyChatMessageEntity assistant = StudyChatMessageEntity.assistant(
                UUID.randomUUID(),
                prepared.sessionId(),
                grounded.answer(),
                citations,
                Instant.now()
        );
        messages.save(assistant);
        StudyChatSessionEntity session = sessions.findByIdAndUserId(prepared.sessionId(), prepared.userId())
                .orElseThrow(StudyChatService::notFound);
        session.touch(Instant.now());
        sessions.save(session);

        log.info(
                "Study chat message session={} course={} user={} history={} retrieved={} cited={} answerChars={}",
                prepared.sessionId(),
                prepared.courseId(),
                prepared.userId(),
                prepared.history().size(),
                retrieved.size(),
                citations.size(),
                grounded.answer().length()
        );
        return toView(session, messages.findBySessionIdOrderByCreatedAtAsc(prepared.sessionId()));
    }

    @Transactional
    public void clearCourseChats(UUID courseId, UUID userId) {
        sessions.deleteByCourseIdAndUserId(courseId, userId);
    }

    @Transactional
    public void clearUserChats(UUID userId) {
        sessions.deleteByUserId(userId);
    }

    private SessionView toView(StudyChatSessionEntity session, List<StudyChatMessageEntity> thread) {
        List<MessageView> views = new ArrayList<>(thread.size());
        for (StudyChatMessageEntity message : thread) {
            views.add(new MessageView(
                    message.getId(),
                    message.getRole(),
                    message.getContent(),
                    message.getCitations().stream()
                            .map(citation -> new RagService.Citation(
                                    citation.materialId(),
                                    citation.title(),
                                    citation.sectionHint(),
                                    citation.pageNumber(),
                                    citation.excerpt()
                            ))
                            .toList(),
                    message.getCreatedAt()
            ));
        }
        return new SessionView(
                session.getId(),
                session.getCourseId(),
                session.getMaterialIds(),
                session.getLanguage(),
                views,
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private List<UUID> normalizeMaterials(UUID courseId, List<UUID> materialIds) {
        if (materialIds == null || materialIds.isEmpty() || materialIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick at least one lecture to study.");
        }
        List<UUID> selected = materialIds.stream().distinct().toList();
        if (chunks.countPublishedReadyMaterials(courseId, selected) != selected.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Every selected material must be ready, published, and part of this course."
            );
        }
        return selected;
    }

    private void requireEnrolled(UUID courseId, UUID userId) {
        CourseEntity course = courses.findById(courseId).orElseThrow(StudyChatService::notFound);
        boolean owner = course.getOwnerId().equals(userId);
        if (!course.isPublished() && !owner) {
            throw notFound();
        }
        if (!enrollments.existsById(new EnrollmentKey(courseId, userId))) {
            throw forbidden();
        }
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

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "That study chat isn’t here anymore.");
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Join this course first to study in chat.");
    }

    public interface StreamListener {
        void onToken(String delta);

        void onDone(SessionView session);

        void onError(String message, int status);
    }

    private record PreparedAsk(
            UUID sessionId,
            UUID courseId,
            UUID userId,
            List<UUID> materialIds,
            String question,
            ExplanationLanguage language,
            List<ChatModel.Turn> history,
            UUID userMessageId
    ) {
    }

    public record SessionView(
            UUID id,
            UUID courseId,
            List<UUID> materialIds,
            String language,
            List<MessageView> messages,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record MessageView(
            UUID id,
            String role,
            String content,
            List<RagService.Citation> citations,
            Instant createdAt
    ) {
    }
}

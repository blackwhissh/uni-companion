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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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

    public StudyChatService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            StudyChatSessionRepository sessions,
            StudyChatMessageRepository messages,
            EmbeddingModel embeddings,
            ChunkRetrievalRepository chunks,
            ChatModel chat
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.sessions = sessions;
        this.messages = messages;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.chat = chat;
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

        ExplanationLanguage language = resolveLanguage(session.getLanguage(), trimmed);
        List<ChatModel.RetrievedChunk> retrieved;
        String rawAnswer;
        try {
            float[] queryEmbedding = embeddings.embed(trimmed);
            retrieved = chunks.findTopK(session.getCourseId(), materialIds, queryEmbedding, TOP_K);
            rawAnswer = chat.answerWithHistory(trimmed, retrieved, language, history);
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
        List<StudyChatMessageEntity.StoredCitation> citations = grounded.chunks().stream()
                .map(chunk -> new StudyChatMessageEntity.StoredCitation(
                        chunk.materialId(),
                        chunk.title(),
                        CitationText.sectionHint(chunk.content()),
                        chunk.pageNumber(),
                        CitationText.excerpt(chunk.content(), trimmed, grounded.answer())
                ))
                .toList();

        StudyChatMessageEntity assistant = StudyChatMessageEntity.assistant(
                UUID.randomUUID(),
                sessionId,
                grounded.answer(),
                citations,
                Instant.now()
        );
        messages.save(assistant);
        session.touch(Instant.now());
        sessions.save(session);

        log.info(
                "Study chat message session={} course={} user={} history={} retrieved={} cited={} answerChars={}",
                sessionId,
                session.getCourseId(),
                userId,
                history.size(),
                retrieved.size(),
                citations.size(),
                grounded.answer().length()
        );
        return toView(session, messages.findBySessionIdOrderByCreatedAtAsc(sessionId));
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

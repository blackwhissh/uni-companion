package com.unicompanion.learning.rag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/rag/chat")
public class StudyChatController {

    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final StudyChatService chats;
    private final TaskExecutor chatStreamExecutor;

    public StudyChatController(
            StudyChatService chats,
            @Qualifier("chatStreamExecutor") TaskExecutor chatStreamExecutor
    ) {
        this.chats = chats;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    @PostMapping("/sessions")
    public SessionResponse start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartRequest request) {
        return SessionResponse.from(chats.start(
                request.courseId(),
                userId(jwt),
                request.materialIds(),
                request.language()
        ));
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return SessionResponse.from(chats.get(sessionId, userId(jwt)));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public SessionResponse ask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @Valid @RequestBody MessageRequest request
    ) {
        return SessionResponse.from(chats.ask(sessionId, userId(jwt), request.question()));
    }

    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @Valid @RequestBody MessageRequest request
    ) {
        UUID uid = userId(jwt);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean completed = new AtomicBoolean(false);
        chatStreamExecutor.execute(() -> {
            try {
                chats.askStreaming(sessionId, uid, request.question(), new StudyChatService.StreamListener() {
                    @Override
                    public void onToken(String delta) {
                        send(emitter, completed, "token", Map.of("delta", delta));
                    }

                    @Override
                    public void onDone(StudyChatService.SessionView session) {
                        send(emitter, completed, "done", SessionResponse.from(session));
                        complete(emitter, completed);
                    }

                    @Override
                    public void onError(String message, int status) {
                        send(emitter, completed, "error", Map.of("message", message, "status", status));
                        complete(emitter, completed);
                    }
                });
            } catch (RuntimeException ex) {
                send(emitter, completed, "error", Map.of(
                        "message", "Could not draft an answer from the published materials. Try again shortly.",
                        "status", 502
                ));
                complete(emitter, completed);
            }
        });
        emitter.onTimeout(() -> complete(emitter, completed));
        emitter.onCompletion(() -> completed.set(true));
        return emitter;
    }

    private static void send(SseEmitter emitter, AtomicBoolean completed, String event, Object data) {
        if (completed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException ex) {
            completed.set(true);
            emitter.completeWithError(ex);
        }
    }

    private static void complete(SseEmitter emitter, AtomicBoolean completed) {
        if (completed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record StartRequest(
            @NotNull UUID courseId,
            @NotEmpty List<@NotNull UUID> materialIds,
            @Size(max = 2) String language
    ) {
    }

    public record MessageRequest(
            @NotBlank @Size(max = 2000, message = "Keep your message to 2000 characters.") String question
    ) {
    }

    public record SessionResponse(
            UUID id,
            UUID courseId,
            List<UUID> materialIds,
            String language,
            List<MessageResponse> messages,
            Instant createdAt,
            Instant updatedAt
    ) {
        static SessionResponse from(StudyChatService.SessionView view) {
            return new SessionResponse(
                    view.id(),
                    view.courseId(),
                    view.materialIds(),
                    view.language(),
                    view.messages().stream().map(MessageResponse::from).toList(),
                    view.createdAt(),
                    view.updatedAt()
            );
        }
    }

    public record MessageResponse(
            UUID id,
            String role,
            String content,
            List<CitationResponse> citations,
            Instant createdAt
    ) {
        static MessageResponse from(StudyChatService.MessageView view) {
            return new MessageResponse(
                    view.id(),
                    view.role(),
                    view.content(),
                    view.citations().stream().map(CitationResponse::from).toList(),
                    view.createdAt()
            );
        }
    }

    public record CitationResponse(
            UUID materialId,
            String title,
            String sectionHint,
            int pageNumber,
            String excerpt
    ) {
        static CitationResponse from(RagService.Citation citation) {
            return new CitationResponse(
                    citation.materialId(),
                    citation.title(),
                    citation.sectionHint(),
                    citation.pageNumber(),
                    citation.excerpt()
            );
        }
    }
}

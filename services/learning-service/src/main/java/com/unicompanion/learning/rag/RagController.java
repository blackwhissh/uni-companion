package com.unicompanion.learning.rag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final long SSE_TIMEOUT_MS = 120_000L;
    private static final int SSE_CHUNK_CHARS = 48;

    private final RagService rag;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "rag-sse");
        thread.setDaemon(true);
        return thread;
    });

    public RagController(RagService rag) {
        this.rag = rag;
    }

    @PostMapping("/query")
    public AnswerResponse query(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody QueryRequest request) {
        RagService.Answer answer = rag.query(
                request.courseId(),
                userId(jwt),
                request.question(),
                request.materialIds(),
                request.priorQuestion(),
                request.priorAnswer()
        );
        return AnswerResponse.from(answer);
    }

    /**
     * Streams status events, then answer text in small deltas, then a final done payload with citations.
     * Generation is still synchronous under the hood (Vertex generateContent); this improves perceived latency.
     */
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryStream(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody QueryRequest request) {
        UUID user = userId(jwt);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sseExecutor.execute(() -> {
            try {
                send(emitter, "status", Map.of("phase", "retrieving"));
                RagService.Answer answer = rag.query(
                        request.courseId(),
                        user,
                        request.question(),
                        request.materialIds(),
                        request.priorQuestion(),
                        request.priorAnswer()
                );
                send(emitter, "status", Map.of("phase", "generating"));
                String text = answer.answer() == null ? "" : answer.answer();
                for (int i = 0; i < text.length(); i += SSE_CHUNK_CHARS) {
                    String delta = text.substring(i, Math.min(i + SSE_CHUNK_CHARS, text.length()));
                    send(emitter, "answer", Map.of("delta", delta));
                }
                send(emitter, "done", AnswerResponse.from(answer));
                emitter.complete();
            } catch (Exception ex) {
                try {
                    String message = ex instanceof org.springframework.web.server.ResponseStatusException rse
                            && rse.getReason() != null
                            ? rse.getReason()
                            : "Could not draft an answer from the published materials. Try again shortly.";
                    int status = ex instanceof org.springframework.web.server.ResponseStatusException rse
                            ? rse.getStatusCode().value()
                            : 502;
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("status", status);
                    error.put("message", message);
                    send(emitter, "error", error);
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(ex);
                }
            }
        });
        return emitter;
    }

    @GetMapping("/courses/{courseId}/history")
    public HistoryResponse history(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @RequestParam(required = false) Integer limit
    ) {
        List<RagService.HistoryTurn> turns = rag.history(courseId, userId(jwt), limit);
        return new HistoryResponse(
                turns.stream()
                        .map(turn -> new HistoryTurnResponse(
                                turn.id(),
                                turn.question(),
                                turn.answer(),
                                turn.citations().stream().map(CitationResponse::from).toList(),
                                turn.insufficient(),
                                turn.latencyMs(),
                                turn.citedCount(),
                                turn.followUp(),
                                turn.createdAt()
                        ))
                        .toList()
        );
    }

    @GetMapping("/courses/{courseId}/stats")
    public StatsResponse stats(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        RagService.CourseStats stats = rag.stats(courseId, userId(jwt));
        return new StatsResponse(
                stats.totalTurns(),
                stats.avgLatencyMs(),
                stats.citeRate(),
                stats.insufficientRate()
        );
    }

    private static void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record QueryRequest(
            @NotNull UUID courseId,
            @NotBlank @Size(max = 2000, message = "Question must be at most 2000 characters.") String question,
            @NotEmpty List<@NotNull UUID> materialIds,
            @Size(max = 4000) String priorQuestion,
            @Size(max = 4000) String priorAnswer
    ) {
    }

    public record AnswerResponse(String answer, List<CitationResponse> citations) {
        static AnswerResponse from(RagService.Answer answer) {
            return new AnswerResponse(
                    answer.answer(),
                    answer.citations().stream().map(CitationResponse::from).toList()
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

    public record HistoryResponse(List<HistoryTurnResponse> turns) {
    }

    public record HistoryTurnResponse(
            UUID id,
            String question,
            String answer,
            List<CitationResponse> citations,
            boolean insufficient,
            int latencyMs,
            int citedCount,
            boolean followUp,
            Instant createdAt
    ) {
    }

    public record StatsResponse(long totalTurns, int avgLatencyMs, double citeRate, double insufficientRate) {
    }
}

package com.unicompanion.learning.rag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService rag;

    public RagController(RagService rag) {
        this.rag = rag;
    }

    @PostMapping("/query")
    public AnswerResponse query(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody QueryRequest request) {
        RagService.Answer answer = rag.query(
                request.courseId(),
                userId(jwt),
                request.question(),
                request.materialIds()
        );
        return AnswerResponse.from(answer);
    }

    @PostMapping("/search")
    public SearchResponse search(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SearchRequest request) {
        RagService.SearchResult result = rag.search(
                request.courseId(),
                userId(jwt),
                request.query(),
                request.materialIds()
        );
        return new SearchResponse(result.hits().stream()
                .map(hit -> new SearchHitResponse(hit.materialId(), hit.title(), hit.pageNumber(), hit.excerpt()))
                .toList());
    }

    @PostMapping("/summary")
    public AnswerResponse summarize(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SummaryRequest request) {
        return AnswerResponse.from(rag.summarize(request.courseId(), userId(jwt), request.materialId()));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record SearchRequest(
            @NotNull UUID courseId,
            @NotBlank @Size(max = 200, message = "Search must be at most 200 characters.") String query,
            List<@NotNull UUID> materialIds
    ) {
    }

    public record SummaryRequest(
            @NotNull UUID courseId,
            @NotNull UUID materialId
    ) {
    }

    public record SearchResponse(List<SearchHitResponse> hits) {
    }

    public record SearchHitResponse(UUID materialId, String title, int pageNumber, String excerpt) {
    }

    public record QueryRequest(
            @NotNull UUID courseId,
            @NotBlank @Size(max = 2000, message = "Question must be at most 2000 characters.") String question,
            @NotEmpty List<@NotNull UUID> materialIds
    ) {
    }

    public record AnswerResponse(String answer, List<CitationResponse> citations) {
        static AnswerResponse from(RagService.Answer answer) {
            return new AnswerResponse(
                    answer.answer(),
                    answer.citations().stream()
                            .map(citation -> new CitationResponse(
                                    citation.materialId(),
                                    citation.title(),
                                    citation.sectionHint(),
                                    citation.pageNumber(),
                                    citation.excerpt()
                            ))
                            .toList()
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
    }
}

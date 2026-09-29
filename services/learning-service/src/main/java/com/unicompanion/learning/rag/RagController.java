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

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record QueryRequest(
            @NotNull UUID courseId,
            @NotBlank @Size(max = 2000) String question,
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

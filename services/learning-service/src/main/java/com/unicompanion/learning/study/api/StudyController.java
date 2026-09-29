package com.unicompanion.learning.study.api;

import com.unicompanion.learning.study.application.StudyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/study")
public class StudyController {

    private final StudyService study;

    public StudyController(StudyService study) {
        this.study = study;
    }

    @PostMapping("/courses/{courseId}/flashcards/generate")
    public ResponseEntity<StudyService.DeckView> generateFlashcards(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestBody(required = false) GenerateRequest request
    ) {
        StudyService.GenerateDeckResult result = study.generateFlashcards(
                courseId,
                userId(jwt),
                force,
                materialIds(request)
        );
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.view());
    }

    @GetMapping("/courses/{courseId}/flashcards")
    public StudyService.DeckView listFlashcards(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam List<UUID> materialIds
    ) {
        return study.latestFlashcards(courseId, userId(jwt), materialIds);
    }

    @PostMapping("/courses/{courseId}/flashcards/select")
    public StudyService.DeckView selectFlashcards(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SelectVersionRequest request
    ) {
        return study.selectFlashcardVersion(courseId, userId(jwt), request.materialIds(), request.versionId());
    }

    @PostMapping("/flashcards/{deckId}/report")
    public StudyService.ReportResult reportFlashcards(
            @PathVariable UUID deckId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) ReportRequest request
    ) {
        return study.reportFlashcardVersion(deckId, userId(jwt), request == null ? "" : request.reason());
    }

    @PostMapping("/courses/{courseId}/quizzes/generate")
    public ResponseEntity<StudyService.QuizView> generateQuiz(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestBody(required = false) GenerateRequest request
    ) {
        StudyService.GenerateQuizResult result = study.generateQuiz(
                courseId,
                userId(jwt),
                force,
                materialIds(request)
        );
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.view());
    }

    @GetMapping("/courses/{courseId}/quizzes")
    public StudyService.QuizView listQuiz(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam List<UUID> materialIds
    ) {
        return study.latestQuiz(courseId, userId(jwt), materialIds);
    }

    @PostMapping("/courses/{courseId}/quizzes/select")
    public StudyService.QuizView selectQuiz(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SelectVersionRequest request
    ) {
        return study.selectQuizVersion(courseId, userId(jwt), request.materialIds(), request.versionId());
    }

    @PostMapping("/quizzes/{quizId}/report")
    public StudyService.ReportResult reportQuiz(
            @PathVariable UUID quizId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) ReportRequest request
    ) {
        return study.reportQuizVersion(quizId, userId(jwt), request == null ? "" : request.reason());
    }

    @PostMapping("/quizzes/{quizId}/submit")
    public StudyService.AttemptView submitQuiz(
            @PathVariable UUID quizId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SubmitRequest request
    ) {
        return study.submitQuiz(
                quizId,
                userId(jwt),
                request.answers().stream()
                        .map(answer -> new StudyService.AnswerInput(answer.questionId(), answer.selectedIndex()))
                        .toList()
        );
    }

    private static List<UUID> materialIds(GenerateRequest request) {
        if (request == null || request.materialIds() == null) {
            return List.of();
        }
        return request.materialIds();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record GenerateRequest(List<UUID> materialIds) {
    }

    public record SelectVersionRequest(@NotEmpty List<UUID> materialIds, @NotNull UUID versionId) {
    }

    public record ReportRequest(String reason) {
    }

    public record SubmitRequest(@NotEmpty List<AnswerRequest> answers) {
    }

    public record AnswerRequest(@NotNull UUID questionId, int selectedIndex) {
    }
}

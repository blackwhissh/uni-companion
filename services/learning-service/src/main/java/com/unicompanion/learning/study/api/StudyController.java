package com.unicompanion.learning.study.api;

import com.unicompanion.learning.config.IdempotencyService;
import com.unicompanion.learning.study.application.FlashcardReviewService;
import com.unicompanion.learning.study.application.ProgressService;
import com.unicompanion.learning.study.application.StudyActivityService;
import com.unicompanion.learning.study.application.ProgressSummary;
import com.unicompanion.learning.study.application.StudyPlan;
import com.unicompanion.learning.study.application.StudyPlanService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/study")
public class StudyController {

    private final StudyService study;
    private final ProgressService progress;
    private final StudyPlanService plans;
    private final FlashcardReviewService cardReviews;
    private final StudyActivityService activity;
    private final IdempotencyService idempotency;
    private final JsonMapper json;

    public StudyController(
            StudyService study,
            ProgressService progress,
            StudyPlanService plans,
            FlashcardReviewService cardReviews,
            StudyActivityService activity,
            IdempotencyService idempotency,
            JsonMapper json
    ) {
        this.study = study;
        this.progress = progress;
        this.plans = plans;
        this.cardReviews = cardReviews;
        this.activity = activity;
        this.idempotency = idempotency;
        this.json = json;
    }

    @GetMapping("/courses/{courseId}/activity")
    public List<StudyActivityService.MaterialActivity> activity(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return activity.forCourse(courseId, userId(jwt));
    }

    @PostMapping("/courses/{courseId}/flashcards/{cardId}/review")
    public CardReviewResponse reviewCard(
            @PathVariable UUID courseId,
            @PathVariable UUID cardId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CardReviewRequest request
    ) {
        cardReviews.mark(courseId, userId(jwt), cardId, request.front(), request.status());
        return new CardReviewResponse(request.status());
    }

    @PostMapping("/courses/{courseId}/quizzes/practice")
    public StudyService.QuizView practiceMissed(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return study.practiceMissed(courseId, userId(jwt));
    }

    @GetMapping("/courses/{courseId}/progress")
    public ProgressSummary.Result progress(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return progress.view(courseId, userId(jwt));
    }

    @GetMapping("/courses/{courseId}/plan")
    public StudyPlan.Result plan(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return plans.view(courseId, userId(jwt));
    }

    @PutMapping("/courses/{courseId}/plan")
    public StudyPlan.Result savePlan(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PlanRequest request
    ) {
        return plans.save(courseId, userId(jwt), request.examDate());
    }

    @PostMapping("/courses/{courseId}/flashcards/generate")
    public ResponseEntity<StudyService.DeckView> generateFlashcards(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestBody(required = false) GenerateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        UUID uid = userId(jwt);
        List<UUID> materials = materialIds(request);
        String route = "POST /api/study/courses/" + courseId + "/flashcards/generate";
        String fingerprint = IdempotencyService.fingerprint(
                route,
                String.valueOf(force),
                materials.stream().map(UUID::toString).sorted().collect(Collectors.joining(","))
        );
        Optional<IdempotencyService.CachedResponse> cached = idempotency.begin(uid, idempotencyKey, route, fingerprint);
        if (cached.isPresent()) {
            return ResponseEntity.status(cached.get().httpStatus())
                    .body(readJson(cached.get().body(), StudyService.DeckView.class));
        }
        try {
            StudyService.GenerateDeckResult result = study.generateFlashcards(courseId, uid, force, materials);
            activity.record(uid, courseId, materials, StudyActivityService.CARDS);
            int status = result.created() ? HttpStatus.CREATED.value() : HttpStatus.OK.value();
            idempotency.complete(uid, idempotencyKey, status, writeJson(result.view()));
            return ResponseEntity.status(status).body(result.view());
        } catch (RuntimeException ex) {
            idempotency.fail(uid, idempotencyKey);
            throw ex;
        }
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
            @RequestBody(required = false) GenerateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        UUID uid = userId(jwt);
        List<UUID> materials = materialIds(request);
        String route = "POST /api/study/courses/" + courseId + "/quizzes/generate";
        String fingerprint = IdempotencyService.fingerprint(
                route,
                String.valueOf(force),
                materials.stream().map(UUID::toString).sorted().collect(Collectors.joining(","))
        );
        Optional<IdempotencyService.CachedResponse> cached = idempotency.begin(uid, idempotencyKey, route, fingerprint);
        if (cached.isPresent()) {
            return ResponseEntity.status(cached.get().httpStatus())
                    .body(readJson(cached.get().body(), StudyService.QuizView.class));
        }
        try {
            StudyService.GenerateQuizResult result = study.generateQuiz(courseId, uid, force, materials);
            int status = result.created() ? HttpStatus.CREATED.value() : HttpStatus.OK.value();
            idempotency.complete(uid, idempotencyKey, status, writeJson(result.view()));
            return ResponseEntity.status(status).body(result.view());
        } catch (RuntimeException ex) {
            idempotency.fail(uid, idempotencyKey);
            throw ex;
        }
    }

    @GetMapping("/quizzes/{quizId}")
    public StudyService.QuizView openQuiz(
            @PathVariable UUID quizId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return study.openQuiz(quizId, userId(jwt));
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
        UUID uid = userId(jwt);
        StudyService.AttemptView attempt = study.submitQuiz(
                quizId,
                uid,
                request.answers().stream()
                        .map(answer -> new StudyService.AnswerInput(answer.questionId(), answer.selectedIndex()))
                        .toList()
        );
        activity.recordQuiz(uid, quizId);
        return attempt;
    }

    private static List<UUID> materialIds(GenerateRequest request) {
        if (request == null || request.materialIds() == null) {
            return List.of();
        }
        return request.materialIds();
    }

    private String writeJson(Object value) {
        return json.writeValueAsString(value);
    }

    private <T> T readJson(String body, Class<T> type) {
        try {
            return json.readValue(body, type);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not replay idempotent response.", ex);
        }
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record PlanRequest(@NotNull LocalDate examDate) {
    }

    public record CardReviewRequest(@NotNull String front, @NotNull String status) {
    }

    public record CardReviewResponse(String status) {
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

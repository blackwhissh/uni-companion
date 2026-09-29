package com.unicompanion.learning.study.application;

import com.unicompanion.learning.config.GeminiProperties;
import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
import com.unicompanion.learning.rag.ChatModel;
import com.unicompanion.learning.rag.ChunkRetrievalRepository;
import com.unicompanion.learning.study.infrastructure.FlashcardDeckEntity;
import com.unicompanion.learning.study.infrastructure.FlashcardDeckProgressEntity;
import com.unicompanion.learning.study.infrastructure.FlashcardDeckProgressRepository;
import com.unicompanion.learning.study.infrastructure.FlashcardDeckRepository;
import com.unicompanion.learning.study.infrastructure.FlashcardEntity;
import com.unicompanion.learning.study.infrastructure.FlashcardRepository;
import com.unicompanion.learning.study.infrastructure.QuizAttemptEntity;
import com.unicompanion.learning.study.infrastructure.QuizAttemptRepository;
import com.unicompanion.learning.study.infrastructure.QuizEntity;
import com.unicompanion.learning.study.infrastructure.QuizProgressEntity;
import com.unicompanion.learning.study.infrastructure.QuizProgressRepository;
import com.unicompanion.learning.study.infrastructure.QuizQuestionEntity;
import com.unicompanion.learning.study.infrastructure.QuizQuestionRepository;
import com.unicompanion.learning.study.infrastructure.QuizRepository;
import com.unicompanion.learning.study.infrastructure.StudyProgressKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StudyService {

    private static final Logger log = LoggerFactory.getLogger(StudyService.class);
    private static final int SOURCE_CHUNK_LIMIT = 48;
    private static final int SOURCE_CHARACTER_BUDGET = 80_000;
    private static final int MAX_CARD_COUNT = 24;
    private static final int MAX_QUIZ_COUNT = 12;
    private static final int GENERATION_WAIT_MILLIS = 90_000;
    private static final Duration STALE_JOB_AGE = Duration.ofMinutes(3);
    private static final Set<String> FLASHCARD_STOP_WORDS = Set.of(
            "a", "an", "and", "are", "define", "describe", "do", "does", "explain", "for",
            "how", "in", "is", "main", "of", "on", "the", "to", "what", "when", "where",
            "which", "why", "with"
    );

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final ChunkRetrievalRepository chunks;
    private final ChatModel chat;
    private final FlashcardDeckRepository decks;
    private final FlashcardRepository flashcards;
    private final FlashcardDeckProgressRepository deckProgress;
    private final QuizRepository quizzes;
    private final QuizQuestionRepository questions;
    private final QuizProgressRepository quizProgress;
    private final QuizAttemptRepository attempts;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final int maxSharedVersions;
    private final int reportRetirementThreshold;
    private final String generationRecipe;

    public StudyService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            ChunkRetrievalRepository chunks,
            ChatModel chat,
            FlashcardDeckRepository decks,
            FlashcardRepository flashcards,
            FlashcardDeckProgressRepository deckProgress,
            QuizRepository quizzes,
            QuizQuestionRepository questions,
            QuizProgressRepository quizProgress,
            QuizAttemptRepository attempts,
            JdbcTemplate jdbc,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            GeminiProperties gemini,
            @Value("${unicompanion.study.max-shared-versions:3}") int maxSharedVersions,
            @Value("${unicompanion.study.report-retirement-threshold:3}") int reportRetirementThreshold,
            @Value("${unicompanion.study.generation-revision:v3}") String generationRevision
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.chunks = chunks;
        this.chat = chat;
        this.decks = decks;
        this.flashcards = flashcards;
        this.deckProgress = deckProgress;
        this.quizzes = quizzes;
        this.questions = questions;
        this.quizProgress = quizProgress;
        this.attempts = attempts;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.maxSharedVersions = Math.max(1, maxSharedVersions);
        this.reportRetirementThreshold = Math.max(1, reportRetirementThreshold);
        this.generationRecipe = generationRevision + ":" + gemini.chatModel() + ":" + chat.getClass().getSimpleName();
    }

    public GenerateDeckResult generateFlashcards(UUID courseId, UUID userId, boolean force, List<UUID> materialIds) {
        requireEnrollment(courseId, userId);
        String materialsKey = materialsKey(materialIds);
        validateSelectedMaterials(courseId, materialIds);

        for (int attempt = 0; attempt < 2; attempt++) {
            GenerationDecision decision = transactions.execute(status ->
                    decideFlashcards(courseId, userId, materialsKey, force)
            );
            if (decision == null) {
                throw unavailable("Could not prepare flashcards.");
            }
            if (decision.versionId() != null) {
                return new GenerateDeckResult(toDeckView(decision.versionId(), decision.delivery()), false);
            }
            if (decision.jobId() != null) {
                try {
                    List<ChatModel.RetrievedChunk> source = requirePublishedChunks(courseId, materialIds);
                    int requestedCards = Math.min(MAX_CARD_COUNT, Math.max(8, source.size() * 2));
                    List<ChatModel.FlashcardDraft> existingCards = existingFlashcards(courseId, materialsKey);
                    List<String> avoided = avoidedFlashcardSummaries(existingCards);
                    final List<ChatModel.FlashcardDraft> drafts;
                    try {
                        List<ChatModel.FlashcardDraft> candidates = chat.generateFlashcards(
                                source,
                                requestedCards,
                                avoided
                        );
                        List<ChatModel.FlashcardDraft> novel = filterNovelFlashcards(candidates, existingCards);
                        if (novel.isEmpty() && !existingCards.isEmpty()) {
                            log.warn(
                                    "Flashcard novelty filter removed all {} candidates; regenerating with stronger variety",
                                    candidates.size()
                            );
                            List<String> strongerAvoid = new ArrayList<>(avoided);
                            strongerAvoid.add(
                                    "CRITICAL: Prior shared decks already cover these. Produce entirely different facts, angles, and examples."
                            );
                            candidates = chat.generateFlashcards(source, requestedCards, strongerAvoid);
                            novel = filterNovelFlashcards(candidates, existingCards);
                            if (novel.isEmpty() && !candidates.isEmpty()) {
                                novel = filterExactFrontDuplicates(candidates, existingCards);
                                log.warn(
                                        "Flashcard soft filter kept {} of {} after novelty wipe",
                                        novel.size(),
                                        candidates.size()
                                );
                            }
                        }
                        drafts = novel;
                    } catch (IllegalStateException ex) {
                        log.error("Flashcard model returned unusable output course={} materials={}", courseId, materialsKey, ex);
                        throw new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "The model returned no usable flashcards.",
                                ex
                        );
                    }
                    validateFlashcards(drafts);
                    UUID deckId = transactions.execute(status ->
                            saveGeneratedDeck(
                                    courseId,
                                    userId,
                                    materialsKey,
                                    materialIds,
                                    decision.jobId(),
                                    drafts
                            )
                    );
                    if (deckId == null) {
                        throw unavailable("Could not save flashcards.");
                    }
                    return new GenerateDeckResult(toDeckView(deckId, Delivery.CREATED), true);
                } catch (RuntimeException ex) {
                    failGenerationJob(decision.jobId(), ex);
                    throw remapStudyFailure(ex, "flashcards");
                }
            }
            waitForGeneration("FLASHCARDS", courseId, materialsKey);
        }
        throw unavailable("Flashcard generation is still in progress. Try again shortly.");
    }

    @Transactional(readOnly = true)
    public DeckView latestFlashcards(UUID courseId, UUID userId, List<UUID> materialIds) {
        requireEnrollment(courseId, userId);
        String materialsKey = materialsKey(materialIds);
        validateSelectedMaterials(courseId, materialIds);
        StudyProgressKey progressKey = new StudyProgressKey(courseId, userId, materialsKey);
        FlashcardDeckProgressEntity progress = deckProgress.findById(progressKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No flashcard deck yet."));
        FlashcardDeckEntity deck = decks.findById(progress.getDeckId()).orElseThrow(StudyService::notFound);
        if (!deck.isActive() || !deck.getMaterialsKey().equals(materialsKey)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active flashcard deck yet.");
        }
        return toDeckView(deck, Delivery.RESUMED);
    }

    public GenerateQuizResult generateQuiz(UUID courseId, UUID userId, boolean force, List<UUID> materialIds) {
        requireEnrollment(courseId, userId);
        String materialsKey = materialsKey(materialIds);
        validateSelectedMaterials(courseId, materialIds);

        for (int attempt = 0; attempt < 2; attempt++) {
            GenerationDecision decision = transactions.execute(status ->
                    decideQuiz(courseId, userId, materialsKey, force)
            );
            if (decision == null) {
                throw unavailable("Could not prepare a quiz.");
            }
            if (decision.versionId() != null) {
                return new GenerateQuizResult(toQuizView(decision.versionId(), decision.delivery()), false);
            }
            if (decision.jobId() != null) {
                try {
                    List<ChatModel.RetrievedChunk> source = requirePublishedChunks(courseId, materialIds);
                    int requestedQuestions = Math.min(MAX_QUIZ_COUNT, Math.max(6, source.size()));
                    List<ChatModel.QuizQuestionDraft> existingQuestions = existingQuizQuestions(courseId, materialsKey);
                    List<ChatModel.QuizQuestionDraft> drafts;
                    try {
                        drafts = filterNovelQuizQuestions(
                                chat.generateQuiz(source, requestedQuestions, avoidedQuizSummaries(existingQuestions)),
                                existingQuestions
                        );
                    } catch (IllegalStateException ex) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "The model returned no usable quiz.",
                                ex
                        );
                    }
                    validateQuiz(drafts);
                    UUID quizId = transactions.execute(status ->
                            saveGeneratedQuiz(
                                    courseId,
                                    userId,
                                    materialsKey,
                                    materialIds,
                                    decision.jobId(),
                                    drafts
                            )
                    );
                    if (quizId == null) {
                        throw unavailable("Could not save the quiz.");
                    }
                    return new GenerateQuizResult(toQuizView(quizId, Delivery.CREATED), true);
                } catch (RuntimeException ex) {
                    failGenerationJob(decision.jobId(), ex);
                    throw remapStudyFailure(ex, "a quiz");
                }
            }
            waitForGeneration("QUIZ", courseId, materialsKey);
        }
        throw unavailable("Quiz generation is still in progress. Try again shortly.");
    }

    @Transactional(readOnly = true)
    public QuizView latestQuiz(UUID courseId, UUID userId, List<UUID> materialIds) {
        requireEnrollment(courseId, userId);
        String materialsKey = materialsKey(materialIds);
        validateSelectedMaterials(courseId, materialIds);
        StudyProgressKey progressKey = new StudyProgressKey(courseId, userId, materialsKey);
        QuizProgressEntity progress = quizProgress.findById(progressKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No quiz yet."));
        QuizEntity quiz = quizzes.findById(progress.getQuizId()).orElseThrow(StudyService::notFound);
        if (!quiz.isActive() || !quiz.getMaterialsKey().equals(materialsKey)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active quiz yet.");
        }
        return toQuizView(quiz, Delivery.RESUMED);
    }

    @Transactional
    public DeckView selectFlashcardVersion(
            UUID courseId,
            UUID userId,
            List<UUID> materialIds,
            UUID deckId
    ) {
        requireEnrollment(courseId, userId);
        String materialsKey = materialsKey(materialIds);
        validateSelectedMaterials(courseId, materialIds);
        FlashcardDeckEntity deck = decks.findById(deckId).orElseThrow(StudyService::notFound);
        if (!deck.isActive()
                || !deck.getCourseId().equals(courseId)
                || !deck.getMaterialsKey().equals(materialsKey)) {
            throw notFound();
        }
        saveDeckProgress(new StudyProgressKey(courseId, userId, materialsKey), deckId);
        recordDelivery("FLASHCARDS", courseId, userId, materialsKey, deckId, Delivery.SELECTED);
        return toDeckView(deck, Delivery.SELECTED);
    }

    @Transactional
    public QuizView selectQuizVersion(
            UUID courseId,
            UUID userId,
            List<UUID> materialIds,
            UUID quizId
    ) {
        requireEnrollment(courseId, userId);
        String materialsKey = materialsKey(materialIds);
        validateSelectedMaterials(courseId, materialIds);
        QuizEntity quiz = quizzes.findById(quizId).orElseThrow(StudyService::notFound);
        if (!quiz.isActive()
                || !quiz.getCourseId().equals(courseId)
                || !quiz.getMaterialsKey().equals(materialsKey)) {
            throw notFound();
        }
        saveQuizProgress(new StudyProgressKey(courseId, userId, materialsKey), quizId);
        recordDelivery("QUIZ", courseId, userId, materialsKey, quizId, Delivery.SELECTED);
        return toQuizView(quiz, Delivery.SELECTED);
    }

    @Transactional
    public ReportResult reportFlashcardVersion(UUID deckId, UUID userId, String reason) {
        FlashcardDeckEntity deck = decks.findById(deckId).orElseThrow(StudyService::notFound);
        requireEnrollment(deck.getCourseId(), userId);
        boolean retired = recordReportAndCheckRetirement("FLASHCARDS", deckId, userId, reason);
        if (retired && deck.isActive()) {
            deck.retire("Community quality reports", Instant.now());
            decks.save(deck);
        }
        return new ReportResult(!deck.isActive());
    }

    @Transactional
    public ReportResult reportQuizVersion(UUID quizId, UUID userId, String reason) {
        QuizEntity quiz = quizzes.findById(quizId).orElseThrow(StudyService::notFound);
        requireEnrollment(quiz.getCourseId(), userId);
        boolean retired = recordReportAndCheckRetirement("QUIZ", quizId, userId, reason);
        if (retired && quiz.isActive()) {
            quiz.retire("Community quality reports", Instant.now());
            quizzes.save(quiz);
        }
        return new ReportResult(!quiz.isActive());
    }

    @Transactional
    public AttemptView submitQuiz(UUID quizId, UUID userId, List<AnswerInput> answers) {
        QuizEntity quiz = quizzes.findById(quizId).orElseThrow(StudyService::notFound);
        requireEnrollment(quiz.getCourseId(), userId);
        List<QuizQuestionEntity> quizQuestions = questions.findByQuizIdOrderBySortOrderAsc(quizId);
        Map<UUID, Integer> selectedByQuestion = answers.stream()
                .collect(Collectors.toMap(AnswerInput::questionId, AnswerInput::selectedIndex, (a, b) -> b));
        int score = 0;
        List<AnswerReview> reviews = new ArrayList<>();
        for (QuizQuestionEntity question : quizQuestions) {
            int selected = selectedByQuestion.getOrDefault(question.getId(), -1);
            boolean correct = selected == question.getCorrectIndex();
            if (correct) {
                score++;
            }
            List<String> options = readOptions(question.getOptionsJson());
            String correctOption = question.getCorrectIndex() >= 0 && question.getCorrectIndex() < options.size()
                    ? options.get(question.getCorrectIndex())
                    : "";
            reviews.add(new AnswerReview(
                    question.getId(),
                    selected,
                    question.getCorrectIndex(),
                    correct,
                    correctOption,
                    question.getExplanation()
            ));
        }
        QuizAttemptEntity attempt = attempts.save(QuizAttemptEntity.create(
                UUID.randomUUID(),
                quizId,
                userId,
                score,
                quizQuestions.size(),
                Instant.now()
        ));
        log.info("Quiz attempt quiz={} user={} score={}/{}", quizId, userId, score, quizQuestions.size());
        return new AttemptView(attempt.getId(), attempt.getScore(), attempt.getTotal(), reviews);
    }

    private GenerationDecision decideFlashcards(
            UUID courseId,
            UUID userId,
            String materialsKey,
            boolean advance
    ) {
        StudyProgressKey progressKey = new StudyProgressKey(courseId, userId, materialsKey);
        List<FlashcardDeckEntity> versions =
                decks.findByCourseIdAndMaterialsKeyAndActiveTrueOrderByCreatedAtAsc(courseId, materialsKey);
        Optional<UUID> currentId = deckProgress.findById(progressKey)
                .map(FlashcardDeckProgressEntity::getDeckId)
                .filter(id -> versions.stream().anyMatch(version -> version.getId().equals(id)));

        if (!advance && currentId.isPresent()) {
            recordDelivery("FLASHCARDS", courseId, userId, materialsKey, currentId.get(), Delivery.RESUMED);
            return GenerationDecision.version(currentId.get(), Delivery.RESUMED);
        }
        if (!advance && !versions.isEmpty()) {
            UUID firstId = versions.getFirst().getId();
            saveDeckProgress(progressKey, firstId);
            recordDelivery("FLASHCARDS", courseId, userId, materialsKey, firstId, Delivery.REUSED);
            return GenerationDecision.version(firstId, Delivery.REUSED);
        }
        Optional<FlashcardDeckEntity> next = nextDeckVersion(versions, currentId);
        if (advance && next.isPresent()) {
            saveDeckProgress(progressKey, next.get().getId());
            recordDelivery("FLASHCARDS", courseId, userId, materialsKey, next.get().getId(), Delivery.NEXT_SHARED);
            return GenerationDecision.version(next.get().getId(), Delivery.NEXT_SHARED);
        }
        if (advance && versions.size() >= maxSharedVersions) {
            UUID firstId = versions.getFirst().getId();
            saveDeckProgress(progressKey, firstId);
            recordDelivery("FLASHCARDS", courseId, userId, materialsKey, firstId, Delivery.CYCLED);
            return GenerationDecision.version(firstId, Delivery.CYCLED);
        }
        return acquireGenerationJob("FLASHCARDS", courseId, userId, materialsKey);
    }

    private GenerationDecision decideQuiz(UUID courseId, UUID userId, String materialsKey, boolean advance) {
        StudyProgressKey progressKey = new StudyProgressKey(courseId, userId, materialsKey);
        List<QuizEntity> versions =
                quizzes.findByCourseIdAndMaterialsKeyAndActiveTrueOrderByCreatedAtAsc(courseId, materialsKey);
        Optional<UUID> currentId = quizProgress.findById(progressKey)
                .map(QuizProgressEntity::getQuizId)
                .filter(id -> versions.stream().anyMatch(version -> version.getId().equals(id)));

        if (!advance && currentId.isPresent()) {
            recordDelivery("QUIZ", courseId, userId, materialsKey, currentId.get(), Delivery.RESUMED);
            return GenerationDecision.version(currentId.get(), Delivery.RESUMED);
        }
        if (!advance && !versions.isEmpty()) {
            UUID firstId = versions.getFirst().getId();
            saveQuizProgress(progressKey, firstId);
            recordDelivery("QUIZ", courseId, userId, materialsKey, firstId, Delivery.REUSED);
            return GenerationDecision.version(firstId, Delivery.REUSED);
        }
        Optional<QuizEntity> next = nextQuizVersion(versions, currentId);
        if (advance && next.isPresent()) {
            saveQuizProgress(progressKey, next.get().getId());
            recordDelivery("QUIZ", courseId, userId, materialsKey, next.get().getId(), Delivery.NEXT_SHARED);
            return GenerationDecision.version(next.get().getId(), Delivery.NEXT_SHARED);
        }
        if (advance && versions.size() >= maxSharedVersions) {
            UUID firstId = versions.getFirst().getId();
            saveQuizProgress(progressKey, firstId);
            recordDelivery("QUIZ", courseId, userId, materialsKey, firstId, Delivery.CYCLED);
            return GenerationDecision.version(firstId, Delivery.CYCLED);
        }
        return acquireGenerationJob("QUIZ", courseId, userId, materialsKey);
    }

    private GenerationDecision acquireGenerationJob(String kind, UUID courseId, UUID userId, String materialsKey) {
        expireStaleGenerationJobs(kind, courseId, materialsKey);
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        int inserted = jdbc.update(
                """
                        INSERT INTO study_generation_jobs
                            (id, kind, course_id, pool_key, requested_by, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'RUNNING', ?, ?)
                        ON CONFLICT (kind, course_id, pool_key) WHERE status = 'RUNNING'
                        DO NOTHING
                        """,
                jobId,
                kind,
                courseId,
                materialsKey,
                userId,
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return inserted == 1 ? GenerationDecision.generate(jobId) : GenerationDecision.waitForGeneration();
    }

    private UUID saveGeneratedDeck(
            UUID courseId,
            UUID userId,
            String materialsKey,
            List<UUID> materialIds,
            UUID jobId,
            List<ChatModel.FlashcardDraft> drafts
    ) {
        validateSelectedMaterials(courseId, materialIds);
        Instant now = Instant.now();
        FlashcardDeckEntity deck = decks.saveAndFlush(FlashcardDeckEntity.create(
                UUID.randomUUID(),
                courseId,
                userId,
                materialsKey,
                now
        ));
        int order = 0;
        for (ChatModel.FlashcardDraft draft : drafts) {
            flashcards.save(FlashcardEntity.create(
                    UUID.randomUUID(),
                    deck.getId(),
                    draft.front(),
                    draft.back(),
                    order++
            ));
        }
        for (UUID materialId : materialIds.stream().distinct().toList()) {
            jdbc.update(
                    "INSERT INTO flashcard_deck_materials (deck_id, material_id) VALUES (?, ?)",
                    deck.getId(),
                    materialId
            );
        }
        saveDeckProgress(new StudyProgressKey(courseId, userId, materialsKey), deck.getId());
        completeGenerationJob(jobId, deck.getId());
        recordDelivery("FLASHCARDS", courseId, userId, materialsKey, deck.getId(), Delivery.CREATED);
        log.info("Generated flashcards course={} user={} deck={} cards={}", courseId, userId, deck.getId(), drafts.size());
        return deck.getId();
    }

    private UUID saveGeneratedQuiz(
            UUID courseId,
            UUID userId,
            String materialsKey,
            List<UUID> materialIds,
            UUID jobId,
            List<ChatModel.QuizQuestionDraft> drafts
    ) {
        validateSelectedMaterials(courseId, materialIds);
        Instant now = Instant.now();
        QuizEntity quiz = quizzes.saveAndFlush(QuizEntity.create(
                UUID.randomUUID(),
                courseId,
                userId,
                materialsKey,
                now
        ));
        int order = 0;
        for (ChatModel.QuizQuestionDraft draft : drafts) {
            questions.save(QuizQuestionEntity.create(
                    UUID.randomUUID(),
                    quiz.getId(),
                    draft.prompt(),
                    writeOptions(draft.options()),
                    draft.correctIndex(),
                    draft.explanation(),
                    order++
            ));
        }
        for (UUID materialId : materialIds.stream().distinct().toList()) {
            jdbc.update("INSERT INTO quiz_materials (quiz_id, material_id) VALUES (?, ?)", quiz.getId(), materialId);
        }
        saveQuizProgress(new StudyProgressKey(courseId, userId, materialsKey), quiz.getId());
        completeGenerationJob(jobId, quiz.getId());
        recordDelivery("QUIZ", courseId, userId, materialsKey, quiz.getId(), Delivery.CREATED);
        log.info("Generated quiz course={} user={} quiz={} questions={}", courseId, userId, quiz.getId(), drafts.size());
        return quiz.getId();
    }

    private void saveDeckProgress(StudyProgressKey key, UUID deckId) {
        Instant now = Instant.now();
        FlashcardDeckProgressEntity progress = deckProgress.findById(key)
                .orElseGet(() -> FlashcardDeckProgressEntity.create(key, deckId, now));
        progress.assign(deckId, now);
        deckProgress.save(progress);
    }

    private void saveQuizProgress(StudyProgressKey key, UUID quizId) {
        Instant now = Instant.now();
        QuizProgressEntity progress = quizProgress.findById(key)
                .orElseGet(() -> QuizProgressEntity.create(key, quizId, now));
        progress.assign(quizId, now);
        quizProgress.save(progress);
    }

    private void completeGenerationJob(UUID jobId, UUID versionId) {
        jdbc.update(
                """
                        UPDATE study_generation_jobs
                        SET status = 'COMPLETED', version_id = ?, updated_at = ?
                        WHERE id = ?
                        """,
                versionId,
                Timestamp.from(Instant.now()),
                jobId
        );
    }

    private void failGenerationJob(UUID jobId, RuntimeException error) {
        transactions.executeWithoutResult(status -> jdbc.update(
                """
                        UPDATE study_generation_jobs
                        SET status = 'FAILED', error = ?, updated_at = ?
                        WHERE id = ? AND status = 'RUNNING'
                        """,
                truncate(error.getMessage(), 500),
                Timestamp.from(Instant.now()),
                jobId
        ));
    }

    private void expireStaleGenerationJobs(String kind, UUID courseId, String materialsKey) {
        Instant cutoff = Instant.now().minus(STALE_JOB_AGE);
        int expired = jdbc.update(
                """
                        UPDATE study_generation_jobs
                        SET status = 'FAILED', error = ?, updated_at = ?
                        WHERE kind = ? AND course_id = ? AND pool_key = ? AND status = 'RUNNING'
                          AND updated_at < ?
                        """,
                "Generation stalled and was reset.",
                Timestamp.from(Instant.now()),
                kind,
                courseId,
                materialsKey,
                Timestamp.from(cutoff)
        );
        if (expired > 0) {
            log.warn("Expired {} stale {} generation jobs course={}", expired, kind, courseId);
        }
    }

    private static RuntimeException remapStudyFailure(RuntimeException ex, String noun) {
        if (ex instanceof ResponseStatusException) {
            return ex;
        }
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "The study model could not produce " + noun + ". Try again shortly.",
                ex
        );
    }

    private void waitForGeneration(String kind, UUID courseId, String materialsKey) {
        expireStaleGenerationJobs(kind, courseId, materialsKey);
        long deadline = System.currentTimeMillis() + GENERATION_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            Integer running = jdbc.queryForObject(
                    """
                            SELECT count(*)
                            FROM study_generation_jobs
                            WHERE kind = ? AND course_id = ? AND pool_key = ? AND status = 'RUNNING'
                            """,
                    Integer.class,
                    kind,
                    courseId,
                    materialsKey
            );
            if (running == null || running == 0) {
                return;
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw unavailable("Generation wait was interrupted.");
            }
        }
        throw unavailable("Generation is taking longer than expected. Try again shortly.");
    }

    private void recordDelivery(
            String kind,
            UUID courseId,
            UUID userId,
            String materialsKey,
            UUID versionId,
            Delivery delivery
    ) {
        jdbc.update(
                """
                        INSERT INTO study_delivery_events
                            (id, kind, course_id, user_id, pool_key, version_id, outcome, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                kind,
                courseId,
                userId,
                materialsKey,
                versionId,
                delivery.name(),
                Timestamp.from(Instant.now())
        );
    }

    private boolean recordReportAndCheckRetirement(String kind, UUID versionId, UUID userId, String reason) {
        jdbc.update(
                """
                        INSERT INTO study_version_reports (id, kind, version_id, user_id, reason, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (kind, version_id, user_id) DO NOTHING
                        """,
                UUID.randomUUID(),
                kind,
                versionId,
                userId,
                truncate(reason, 500),
                Timestamp.from(Instant.now())
        );
        Integer reports = jdbc.queryForObject(
                "SELECT count(*) FROM study_version_reports WHERE kind = ? AND version_id = ?",
                Integer.class,
                kind,
                versionId
        );
        return reports != null && reports >= reportRetirementThreshold;
    }

    private List<ChatModel.FlashcardDraft> existingFlashcards(UUID courseId, String materialsKey) {
        return decks.findByCourseIdAndMaterialsKeyAndActiveTrueOrderByCreatedAtAsc(courseId, materialsKey).stream()
                .flatMap(deck -> flashcards.findByDeckIdOrderBySortOrderAsc(deck.getId()).stream())
                .map(card -> new ChatModel.FlashcardDraft(card.getFront(), card.getBack()))
                .toList();
    }

    static List<String> avoidedFlashcardSummaries(List<ChatModel.FlashcardDraft> existingCards) {
        if (existingCards == null || existingCards.isEmpty()) {
            return List.of();
        }
        return existingCards.stream()
                .limit(48)
                .map(StudyService::avoidanceLine)
                .toList();
    }

    static String avoidanceLine(ChatModel.FlashcardDraft card) {
        return "Front: " + truncate(nullToEmpty(card == null ? null : card.front()), 120)
                + " Back: " + truncate(nullToEmpty(card == null ? null : card.back()), 160);
    }

    private List<ChatModel.QuizQuestionDraft> existingQuizQuestions(UUID courseId, String materialsKey) {
        return quizzes.findByCourseIdAndMaterialsKeyAndActiveTrueOrderByCreatedAtAsc(courseId, materialsKey).stream()
                .flatMap(quiz -> questions.findByQuizIdOrderBySortOrderAsc(quiz.getId()).stream())
                .map(this::toQuizDraft)
                .toList();
    }

    private ChatModel.QuizQuestionDraft toQuizDraft(QuizQuestionEntity question) {
        return new ChatModel.QuizQuestionDraft(
                question.getPrompt(),
                readOptions(question.getOptionsJson()),
                question.getCorrectIndex(),
                question.getExplanation()
        );
    }

    static List<String> avoidedQuizSummaries(List<ChatModel.QuizQuestionDraft> existingQuestions) {
        if (existingQuestions == null || existingQuestions.isEmpty()) {
            return List.of();
        }
        return existingQuestions.stream()
                .limit(48)
                .map(StudyService::quizAvoidanceLine)
                .toList();
    }

    static String quizAvoidanceLine(ChatModel.QuizQuestionDraft question) {
        return "Prompt: " + truncate(nullToEmpty(question == null ? null : question.prompt()), 120)
                + " Correct: " + truncate(quizCorrectAnswer(question), 160);
    }

    private List<ChatModel.QuizQuestionDraft> filterNovelQuizQuestions(
            List<ChatModel.QuizQuestionDraft> candidates,
            List<ChatModel.QuizQuestionDraft> existingQuestions
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ChatModel.QuizQuestionDraft> accepted = new ArrayList<>();
        List<ChatModel.QuizQuestionDraft> comparison = new ArrayList<>(existingQuestions);
        for (ChatModel.QuizQuestionDraft candidate : candidates) {
            if (candidate == null || candidate.prompt() == null) {
                continue;
            }
            boolean duplicate = comparison.stream().anyMatch(existing -> similarQuizQuestion(candidate, existing));
            if (!duplicate) {
                accepted.add(candidate);
                comparison.add(candidate);
            }
        }
        int removed = candidates.size() - accepted.size();
        if (removed > 0) {
            log.info("Removed {} similar quiz questions before saving shared version", removed);
        }
        return List.copyOf(accepted);
    }

    static boolean similarQuizQuestion(ChatModel.QuizQuestionDraft left, ChatModel.QuizQuestionDraft right) {
        if (left == null || right == null) {
            return false;
        }
        if (similarFlashcardQuestion(left.prompt(), right.prompt())) {
            return true;
        }
        String leftAnswer = quizCorrectAnswer(left);
        String rightAnswer = quizCorrectAnswer(right);
        String leftNormalized = normalizeQuestion(leftAnswer);
        String rightNormalized = normalizeQuestion(rightAnswer);
        if (leftNormalized.length() >= 40
                && rightNormalized.length() >= 40
                && similarFlashcardQuestion(leftAnswer, rightAnswer)) {
            return true;
        }
        return similarFlashcardQuestion(
                nullToEmpty(left.prompt()) + " " + leftAnswer,
                nullToEmpty(right.prompt()) + " " + rightAnswer
        );
    }

    static String quizCorrectAnswer(ChatModel.QuizQuestionDraft question) {
        if (question == null || question.options() == null) {
            return "";
        }
        int index = question.correctIndex();
        if (index < 0 || index >= question.options().size()) {
            return "";
        }
        return nullToEmpty(question.options().get(index));
    }

    private List<ChatModel.FlashcardDraft> filterNovelFlashcards(
            List<ChatModel.FlashcardDraft> candidates,
            List<ChatModel.FlashcardDraft> existingCards
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ChatModel.FlashcardDraft> accepted = new ArrayList<>();
        List<ChatModel.FlashcardDraft> comparison = new ArrayList<>(existingCards);
        for (ChatModel.FlashcardDraft candidate : candidates) {
            if (candidate == null || candidate.front() == null) {
                continue;
            }
            boolean duplicate = comparison.stream().anyMatch(existing -> similarFlashcard(candidate, existing));
            if (!duplicate) {
                accepted.add(candidate);
                comparison.add(candidate);
            }
        }
        int removed = candidates.size() - accepted.size();
        if (removed > 0) {
            log.info("Removed {} similar flashcards before saving shared version", removed);
        }
        return List.copyOf(accepted);
    }

    private List<ChatModel.FlashcardDraft> filterExactFrontDuplicates(
            List<ChatModel.FlashcardDraft> candidates,
            List<ChatModel.FlashcardDraft> existingCards
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<String> usedFronts = existingCards.stream()
                .map(card -> normalizeQuestion(card.front()))
                .filter(front -> !front.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        List<ChatModel.FlashcardDraft> accepted = new ArrayList<>();
        for (ChatModel.FlashcardDraft candidate : candidates) {
            if (candidate == null || candidate.front() == null || candidate.front().isBlank()) {
                continue;
            }
            String normalized = normalizeQuestion(candidate.front());
            if (usedFronts.contains(normalized)) {
                continue;
            }
            usedFronts.add(normalized);
            accepted.add(candidate);
        }
        return List.copyOf(accepted);
    }

    static boolean similarFlashcard(ChatModel.FlashcardDraft left, ChatModel.FlashcardDraft right) {
        if (left == null || right == null) {
            return false;
        }
        if (similarFlashcardQuestion(left.front(), right.front())) {
            return true;
        }
        String leftBack = normalizeQuestion(left.back());
        String rightBack = normalizeQuestion(right.back());
        if (leftBack.length() >= 40 && rightBack.length() >= 40 && similarFlashcardQuestion(left.back(), right.back())) {
            return true;
        }
        return similarFlashcardQuestion(cardText(left), cardText(right));
    }

    private static String cardText(ChatModel.FlashcardDraft card) {
        return nullToEmpty(card.front()) + " " + nullToEmpty(card.back());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static boolean similarFlashcardQuestion(String left, String right) {
        String normalizedLeft = normalizeQuestion(left);
        String normalizedRight = normalizeQuestion(right);
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) {
            return false;
        }
        if (normalizedLeft.equals(normalizedRight)) {
            return true;
        }
        Set<String> leftTerms = significantTerms(normalizedLeft);
        Set<String> rightTerms = significantTerms(normalizedRight);
        if (!leftTerms.isEmpty() && !rightTerms.isEmpty()) {
            long shared = leftTerms.stream().filter(rightTerms::contains).count();
            double overlap = (double) shared / Math.min(leftTerms.size(), rightTerms.size());
            double union = leftTerms.size() + rightTerms.size() - shared;
            double jaccard = union == 0 ? 0 : shared / union;
            if (overlap >= 0.8 || jaccard >= 0.65) {
                return true;
            }
        }
        int distance = levenshteinDistance(normalizedLeft, normalizedRight);
        double similarity = 1.0 - ((double) distance / Math.max(normalizedLeft.length(), normalizedRight.length()));
        return similarity >= 0.82;
    }

    private static String normalizeQuestion(String value) {
        return value == null
                ? ""
                : value.toLowerCase()
                        .replaceAll("[^\\p{L}\\p{N}]+", " ")
                        .trim()
                        .replaceAll("\\s+", " ");
    }

    private static Set<String> significantTerms(String normalized) {
        return Arrays.stream(normalized.split(" "))
                .filter(term -> term.length() > 1)
                .filter(term -> !FLASHCARD_STOP_WORDS.contains(term))
                .collect(Collectors.toSet());
    }

    private static int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static void validateFlashcards(List<ChatModel.FlashcardDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The model returned no usable flashcards.");
        }
        Set<String> fronts = drafts.stream()
                .map(ChatModel.FlashcardDraft::front)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase())
                .collect(Collectors.toSet());
        boolean malformed = drafts.stream().anyMatch(draft ->
                draft.front() == null
                        || draft.front().isBlank()
                        || draft.front().length() > 500
                        || draft.back() == null
                        || draft.back().isBlank()
                        || draft.back().length() > 8_000
        );
        if (malformed || fronts.size() != drafts.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The generated flashcards did not pass the quality checks."
            );
        }
    }

    private static void validateQuiz(List<ChatModel.QuizQuestionDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The model returned no usable quiz.");
        }
        Set<String> prompts = drafts.stream()
                .map(ChatModel.QuizQuestionDraft::prompt)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase())
                .collect(Collectors.toSet());
        boolean malformed = drafts.stream().anyMatch(draft ->
                draft.prompt() == null
                        || draft.prompt().isBlank()
                        || draft.options() == null
                        || draft.options().size() != 4
                        || draft.options().stream().anyMatch(option -> option == null || option.isBlank())
                        || draft.options().stream().map(option -> option.trim().toLowerCase()).distinct().count() != 4
                        || draft.correctIndex() < 0
                        || draft.correctIndex() >= 4
                        || draft.explanation() == null
                        || draft.explanation().isBlank()
        );
        if (malformed || prompts.size() != drafts.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The generated quiz did not pass the quality checks.");
        }
    }

    private static Optional<FlashcardDeckEntity> nextDeckVersion(
            List<FlashcardDeckEntity> versions,
            Optional<UUID> currentDeckId
    ) {
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        if (currentDeckId.isEmpty()) {
            return Optional.of(versions.getFirst());
        }
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).getId().equals(currentDeckId.get()) && i + 1 < versions.size()) {
                return Optional.of(versions.get(i + 1));
            }
        }
        return Optional.empty();
    }

    private static Optional<QuizEntity> nextQuizVersion(List<QuizEntity> versions, Optional<UUID> currentQuizId) {
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        if (currentQuizId.isEmpty()) {
            return Optional.of(versions.getFirst());
        }
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).getId().equals(currentQuizId.get()) && i + 1 < versions.size()) {
                return Optional.of(versions.get(i + 1));
            }
        }
        return Optional.empty();
    }

    private DeckView toDeckView(UUID deckId, Delivery delivery) {
        FlashcardDeckEntity deck = decks.findById(deckId).orElseThrow(StudyService::notFound);
        return toDeckView(deck, delivery);
    }

    private DeckView toDeckView(FlashcardDeckEntity deck, Delivery delivery) {
        return toDeckView(deck, flashcards.findByDeckIdOrderBySortOrderAsc(deck.getId()), delivery);
    }

    private QuizView toQuizView(UUID quizId, Delivery delivery) {
        QuizEntity quiz = quizzes.findById(quizId).orElseThrow(StudyService::notFound);
        return toQuizView(quiz, delivery);
    }

    private QuizView toQuizView(QuizEntity quiz, Delivery delivery) {
        return toQuizView(quiz, questions.findByQuizIdOrderBySortOrderAsc(quiz.getId()), delivery);
    }

    private List<ChatModel.RetrievedChunk> requirePublishedChunks(UUID courseId, List<UUID> materialIds) {
        validateSelectedMaterials(courseId, materialIds);
        List<ChatModel.RetrievedChunk> sample = chunks.representativePublished(
                courseId,
                materialIds,
                SOURCE_CHUNK_LIMIT,
                SOURCE_CHARACTER_BUDGET
        );
        if (sample.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No ready published content found for the selected materials."
            );
        }
        return sample;
    }

    private void validateSelectedMaterials(UUID courseId, List<UUID> materialIds) {
        int selectedCount = (int) materialIds.stream().distinct().count();
        if (chunks.countPublishedReadyMaterials(courseId, materialIds) != selectedCount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Every selected material must be ready, published, and part of this course."
            );
        }
    }

    private String materialsKey(List<UUID> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one published material.");
        }
        if (materialIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Material IDs cannot be null.");
        }
        String canonical = generationRecipe + ":" + materialIds.stream()
                .map(UUID::toString)
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void requireEnrollment(UUID courseId, UUID userId) {
        CourseEntity course = courses.findById(courseId).orElseThrow(StudyService::notFound);
        if (!course.isPublished()) {
            throw notFound();
        }
        if (!enrollments.existsById(new EnrollmentKey(courseId, userId))) {
            log.warn("Study denied: user={} not enrolled in course={}", userId, courseId);
            throw forbidden();
        }
    }

    private DeckView toDeckView(
            FlashcardDeckEntity deck,
            List<FlashcardEntity> cards,
            Delivery delivery
    ) {
        List<FlashcardDeckEntity> versions =
                decks.findByCourseIdAndMaterialsKeyAndActiveTrueOrderByCreatedAtAsc(
                deck.getCourseId(),
                deck.getMaterialsKey()
        );
        int currentVersion = versionOf(versions.stream().map(FlashcardDeckEntity::getId).toList(), deck.getId());
        return new DeckView(
                deck.getId(),
                deck.getCourseId(),
                currentVersion,
                versions.size(),
                maxSharedVersions,
                delivery,
                versionOptions(
                        versions.stream().map(FlashcardDeckEntity::getId).toList(),
                        versions.stream().map(FlashcardDeckEntity::getCreatedAt).toList(),
                        deck.getId()
                ),
                cards.stream().map(card -> new CardView(card.getId(), card.getFront(), card.getBack())).toList()
        );
    }

    private QuizView toQuizView(
            QuizEntity quiz,
            List<QuizQuestionEntity> quizQuestions,
            Delivery delivery
    ) {
        List<QuizEntity> versions = quizzes.findByCourseIdAndMaterialsKeyAndActiveTrueOrderByCreatedAtAsc(
                quiz.getCourseId(),
                quiz.getMaterialsKey()
        );
        int currentVersion = versionOf(versions.stream().map(QuizEntity::getId).toList(), quiz.getId());
        return new QuizView(
                quiz.getId(),
                quiz.getCourseId(),
                currentVersion,
                versions.size(),
                maxSharedVersions,
                delivery,
                versionOptions(
                        versions.stream().map(QuizEntity::getId).toList(),
                        versions.stream().map(QuizEntity::getCreatedAt).toList(),
                        quiz.getId()
                ),
                quizQuestions.stream()
                        .map(question -> new QuestionView(
                                question.getId(),
                                question.getPrompt(),
                                readOptions(question.getOptionsJson())
                        ))
                        .toList()
        );
    }

    private static List<VersionOption> versionOptions(List<UUID> ids, List<Instant> createdAt, UUID currentId) {
        List<VersionOption> options = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            options.add(new VersionOption(ids.get(i), i + 1, createdAt.get(i), ids.get(i).equals(currentId)));
        }
        return List.copyOf(options);
    }

    private static int versionOf(List<UUID> ids, UUID id) {
        int index = ids.indexOf(id);
        return index < 0 ? 1 : index + 1;
    }

    private static String writeOptions(List<String> options) {
        return String.join("\u001f", options);
    }

    private static List<String> readOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        return Arrays.asList(optionsJson.split("\u001f", -1));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private static String truncate(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    public record AnswerInput(UUID questionId, int selectedIndex) {
    }

    public record GenerateDeckResult(DeckView view, boolean created) {
    }

    public record GenerateQuizResult(QuizView view, boolean created) {
    }

    public record DeckView(
            UUID id,
            UUID courseId,
            int version,
            int availableVersions,
            int maxVersions,
            Delivery delivery,
            List<VersionOption> versions,
            List<CardView> cards
    ) {
    }

    public record CardView(UUID id, String front, String back) {
    }

    public record QuizView(
            UUID id,
            UUID courseId,
            int version,
            int availableVersions,
            int maxVersions,
            Delivery delivery,
            List<VersionOption> versions,
            List<QuestionView> questions
    ) {
    }

    public record QuestionView(UUID id, String prompt, List<String> options) {
    }

    public record VersionOption(UUID id, int version, Instant createdAt, boolean current) {
    }

    public record ReportResult(boolean retired) {
    }

    public enum Delivery {
        CREATED,
        REUSED,
        RESUMED,
        NEXT_SHARED,
        CYCLED,
        SELECTED
    }

    private record GenerationDecision(UUID versionId, UUID jobId, Delivery delivery) {

        private static GenerationDecision version(UUID versionId, Delivery delivery) {
            return new GenerationDecision(versionId, null, delivery);
        }

        private static GenerationDecision generate(UUID jobId) {
            return new GenerationDecision(null, jobId, null);
        }

        private static GenerationDecision waitForGeneration() {
            return new GenerationDecision(null, null, null);
        }
    }

    public record AttemptView(UUID id, int score, int total, List<AnswerReview> reviews) {
    }

    public record AnswerReview(
            UUID questionId,
            int selectedIndex,
            int correctIndex,
            boolean correct,
            String correctOption,
            String explanation
    ) {
    }
}

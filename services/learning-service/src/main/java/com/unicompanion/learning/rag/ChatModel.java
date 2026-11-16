package com.unicompanion.learning.rag;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface ChatModel {

    String answer(String question, List<RetrievedChunk> chunks, ExplanationLanguage language);

    /**
     * Multi-turn study chat. History is prior user/assistant turns (oldest first), excluding the
     * current question. Default falls back to single-turn {@link #answer}.
     */
    default String answerWithHistory(
            String question,
            List<RetrievedChunk> chunks,
            ExplanationLanguage language,
            List<Turn> history
    ) {
        return answer(question, chunks, language);
    }

    /**
     * Stream a multi-turn answer as text deltas. Default generates the full answer then emits it
     * in small chunks so clients can render progressively even without provider streaming.
     */
    default void streamAnswerWithHistory(
            String question,
            List<RetrievedChunk> chunks,
            ExplanationLanguage language,
            List<Turn> history,
            Consumer<String> onDelta
    ) {
        String answer = answerWithHistory(question, chunks, language, history);
        emitChunks(answer, onDelta);
    }

    static void emitChunks(String text, Consumer<String> onDelta) {
        if (text == null || text.isEmpty() || onDelta == null) {
            return;
        }
        int size = 28;
        for (int i = 0; i < text.length(); i += size) {
            onDelta.accept(text.substring(i, Math.min(i + size, text.length())));
        }
    }

    String summarize(String title, List<RetrievedChunk> chunks, ExplanationLanguage language);

    default List<FlashcardDraft> generateFlashcards(List<RetrievedChunk> chunks, int count) {
        return generateFlashcards(chunks, count, List.of());
    }

    List<FlashcardDraft> generateFlashcards(
            List<RetrievedChunk> chunks,
            int count,
            List<String> questionsToAvoid
    );

    default List<QuizQuestionDraft> generateQuiz(List<RetrievedChunk> chunks, int count) {
        return generateQuiz(chunks, count, List.of());
    }

    List<QuizQuestionDraft> generateQuiz(
            List<RetrievedChunk> chunks,
            int count,
            List<String> questionsToAvoid
    );

    record RetrievedChunk(
            UUID chunkId,
            UUID materialId,
            String title,
            int pageNumber,
            String content
    ) {
    }

    /** One prior chat turn. Role is {@code USER} or {@code ASSISTANT}. */
    record Turn(String role, String content) {
    }

    record FlashcardDraft(String front, String back) {
    }

    record QuizQuestionDraft(String prompt, List<String> options, int correctIndex, String explanation) {
    }
}

package com.unicompanion.learning.rag;

import java.util.List;
import java.util.UUID;

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

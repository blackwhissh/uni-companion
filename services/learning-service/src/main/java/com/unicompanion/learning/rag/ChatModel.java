package com.unicompanion.learning.rag;

import java.util.List;
import java.util.UUID;

public interface ChatModel {

    String answer(String question, List<RetrievedChunk> chunks);

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

    record FlashcardDraft(String front, String back) {
    }

    record QuizQuestionDraft(String prompt, List<String> options, int correctIndex, String explanation) {
    }
}

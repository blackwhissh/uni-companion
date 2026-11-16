package com.unicompanion.learning.rag;

import java.util.List;
import java.util.UUID;

/**
 * For a broad study question, replace the nearest-page sample with one section of the lecture.
 */
final class SectionAnswers {

    private SectionAnswers() {
    }

    static List<ChatModel.RetrievedChunk> choose(
            ChunkRetrievalRepository chunks,
            UUID courseId,
            List<UUID> materialIds,
            String question,
            List<ChatModel.RetrievedChunk> hits
    ) {
        if (!BroadStudyQuestion.matches(question)) {
            return hits;
        }
        List<ChatModel.RetrievedChunk> pages = chunks.listReadyPages(courseId, materialIds);
        List<ChatModel.RetrievedChunk> topicPages = TopicSelection.forBroadQuestion(question, pages, hits);
        return topicPages.isEmpty() ? hits : topicPages;
    }
}

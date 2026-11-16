package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Decides whether a retrieved page may be shown as support for a question.
 * Vector nearest-neighbour alone is not enough: the fake embedding is a hash,
 * and a real embedding can still return the closest wrong page.
 */
final class PassageSupport {

    /**
     * Words that show up in questions and titles without naming the fact.
     * "topic" alone must not make "Published topic is replication" answer
     * "What is the secret draft topic?".
     */
    private static final Set<String> GENERIC = Set.of(
            "topic", "topics", "thing", "things", "course", "courses", "lecture", "lectures",
            "material", "materials", "student", "students", "page", "pages", "published",
            "secret", "draft", "question", "answer", "about", "something", "anything"
    );

    private PassageSupport() {
    }

    static List<ChatModel.RetrievedChunk> keep(String question, List<ChatModel.RetrievedChunk> retrieved) {
        if (retrieved == null || retrieved.isEmpty()) {
            return List.of();
        }
        List<ChatModel.RetrievedChunk> supported = new ArrayList<>();
        for (ChatModel.RetrievedChunk chunk : retrieved) {
            if (chunk != null && supports(question, chunk.content())) {
                supported.add(chunk);
            }
        }
        return List.copyOf(supported);
    }

    static boolean supports(String question, String content) {
        Set<String> questionTerms = LexicalTerms.significant(question);
        Set<String> chunkTerms = LexicalTerms.significant(content);
        if (questionTerms.isEmpty() || chunkTerms.isEmpty()) {
            return false;
        }
        int shared = 0;
        boolean distinctive = false;
        for (String term : questionTerms) {
            if (!chunkTerms.contains(term)) {
                continue;
            }
            shared++;
            if (isDistinctive(term)) {
                distinctive = true;
            }
        }
        return distinctive || shared >= 2;
    }

    private static boolean isDistinctive(String term) {
        if (term.chars().allMatch(Character::isDigit)) {
            return true;
        }
        return term.length() >= 4 && !GENERIC.contains(term);
    }
}

package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks one topic from a lecture and the pages that teach it, in reading order.
 */
final class TopicSelection {

    static final int MAX_PAGES = 8;

    private static final Set<String> STUDY_WORDS = Set.of(
            "walk", "through", "core", "process", "algorithm", "algorithms", "step", "steps",
            "citing", "lecture", "lectures", "explain", "main", "idea", "ideas", "material",
            "materials", "summarize", "summarise", "overview", "topic", "topics", "schritt",
            "schritte", "vorlesung", "hauptidee", "kernprozess", "zusammenfassung", "überblick"
    );

    private TopicSelection() {
    }

    static List<ChatModel.RetrievedChunk> forBroadQuestion(
            String question,
            List<ChatModel.RetrievedChunk> pagesInOrder,
            List<ChatModel.RetrievedChunk> hits
    ) {
        if (pagesInOrder == null || pagesInOrder.isEmpty()) {
            return List.of();
        }
        List<String> topics = TopicOutline.topics(
                pagesInOrder.stream().map(ChatModel.RetrievedChunk::content).toList()
        );
        Map<String, List<ChatModel.RetrievedChunk>> byTopic = new LinkedHashMap<>();
        for (int i = 0; i < pagesInOrder.size(); i++) {
            byTopic.computeIfAbsent(topics.get(i), ignored -> new ArrayList<>()).add(pagesInOrder.get(i));
        }
        String topic = choose(question, byTopic, pagesInOrder, topics, hits);
        List<ChatModel.RetrievedChunk> topicPages = byTopic.getOrDefault(topic, List.of());
        List<ChatModel.RetrievedChunk> spread = spread(topicPages, MAX_PAGES);
        List<ChatModel.RetrievedChunk> labeled = new ArrayList<>(spread.size());
        for (ChatModel.RetrievedChunk page : spread) {
            labeled.add(new ChatModel.RetrievedChunk(
                    page.chunkId(),
                    page.materialId(),
                    page.title() + " · " + topic,
                    page.pageNumber(),
                    page.content()
            ));
        }
        return List.copyOf(labeled);
    }

    private static String choose(
            String question,
            Map<String, List<ChatModel.RetrievedChunk>> byTopic,
            List<ChatModel.RetrievedChunk> pagesInOrder,
            List<String> topics,
            List<ChatModel.RetrievedChunk> hits
    ) {
        Set<String> questionTerms = new LinkedHashSet<>(LexicalTerms.significant(question));
        questionTerms.removeAll(STUDY_WORDS);
        String bestTopic = null;
        int bestScore = 0;
        int bestExtra = Integer.MAX_VALUE;
        for (Map.Entry<String, List<ChatModel.RetrievedChunk>> entry : byTopic.entrySet()) {
            int score = score(questionTerms, entry.getKey(), entry.getValue());
            int extra = extraTitleTerms(questionTerms, entry.getKey());
            if (score > bestScore || (score == bestScore && score > 0 && extra < bestExtra)) {
                bestScore = score;
                bestExtra = extra;
                bestTopic = entry.getKey();
            }
        }
        if (bestTopic != null) {
            return bestTopic;
        }
        String fromHit = topicOfFirstBodyHit(hits, pagesInOrder, topics);
        if (fromHit != null) {
            return fromHit;
        }
        for (Map.Entry<String, List<ChatModel.RetrievedChunk>> entry : byTopic.entrySet()) {
            if (hasBody(entry.getValue())) {
                return entry.getKey();
            }
        }
        return byTopic.keySet().iterator().next();
    }

    private static int extraTitleTerms(Set<String> questionTerms, String topic) {
        int extra = 0;
        for (String term : LexicalTerms.significant(topic)) {
            if (!questionTerms.contains(term)) {
                extra++;
            }
        }
        return extra;
    }

    private static int score(
            Set<String> questionTerms,
            String topic,
            List<ChatModel.RetrievedChunk> pages
    ) {
        if (questionTerms.isEmpty()) {
            return 0;
        }
        Set<String> titleTerms = LexicalTerms.significant(topic);
        int score = 0;
        for (String term : questionTerms) {
            if (titleTerms.contains(term)) {
                score += 5;
            }
        }
        for (ChatModel.RetrievedChunk page : pages) {
            Set<String> pageTerms = LexicalTerms.significant(page.content());
            for (String term : questionTerms) {
                if (pageTerms.contains(term)) {
                    score += 1;
                }
            }
        }
        return score;
    }

    private static String topicOfFirstBodyHit(
            List<ChatModel.RetrievedChunk> hits,
            List<ChatModel.RetrievedChunk> pagesInOrder,
            List<String> topics
    ) {
        if (hits == null) {
            return null;
        }
        for (ChatModel.RetrievedChunk hit : hits) {
            if (hit.content() == null || hit.content().trim().length() <= 250) {
                continue;
            }
            for (int i = 0; i < pagesInOrder.size(); i++) {
                if (hit.chunkId().equals(pagesInOrder.get(i).chunkId())) {
                    return topics.get(i);
                }
            }
        }
        return null;
    }

    private static boolean hasBody(List<ChatModel.RetrievedChunk> pages) {
        for (ChatModel.RetrievedChunk page : pages) {
            if (page.content() != null && page.content().trim().length() > 250) {
                return true;
            }
        }
        return false;
    }

    private static List<ChatModel.RetrievedChunk> spread(List<ChatModel.RetrievedChunk> pages, int max) {
        if (pages.size() <= max) {
            return List.copyOf(pages);
        }
        LinkedHashSet<Integer> indexes = new LinkedHashSet<>();
        indexes.add(0);
        int rest = pages.size() - 1;
        int slots = max - 1;
        for (int i = 1; i <= slots; i++) {
            indexes.add((int) Math.round((double) i * rest / slots));
        }
        List<ChatModel.RetrievedChunk> chosen = new ArrayList<>();
        for (int index : indexes) {
            if (chosen.size() >= max) {
                break;
            }
            chosen.add(pages.get(index));
        }
        return List.copyOf(chosen);
    }
}

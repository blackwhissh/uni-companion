package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps Sources aligned with inline [n] markers in the answer text.
 */
final class CitedSources {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");

    private CitedSources() {
    }

    /**
     * Returns only chunks referenced as [n] in {@code answer}, renumbered 1..k in first-mention order.
     * Out-of-range markers are stripped. If the model cited nothing valid, falls back to all retrieved
     * chunks and strips any leftover invalid markers.
     */
    static Result select(String answer, List<ChatModel.RetrievedChunk> retrieved) {
        String text = answer == null ? "" : answer;
        if (retrieved == null || retrieved.isEmpty()) {
            return new Result(stripAllCitations(text), List.of());
        }
        Set<Integer> cited = firstMentionOrder(text, retrieved.size());
        if (cited.isEmpty()) {
            return new Result(stripAllCitations(text), List.copyOf(retrieved));
        }

        List<ChatModel.RetrievedChunk> selected = new ArrayList<>();
        int[] oldToNew = new int[retrieved.size() + 1];
        int next = 1;
        for (int oldIndex : cited) {
            selected.add(retrieved.get(oldIndex - 1));
            oldToNew[oldIndex] = next++;
        }
        return new Result(rewriteCitations(text, oldToNew), List.copyOf(selected));
    }

    private static Set<Integer> firstMentionOrder(String answer, int maxIndex) {
        Set<Integer> ordered = new LinkedHashSet<>();
        if (answer == null || answer.isBlank()) {
            return ordered;
        }
        Matcher matcher = CITATION.matcher(answer);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index >= 1 && index <= maxIndex) {
                ordered.add(index);
            }
        }
        return ordered;
    }

    private static String rewriteCitations(String answer, int[] oldToNew) {
        Matcher matcher = CITATION.matcher(answer);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            int oldIndex = Integer.parseInt(matcher.group(1));
            int mapped = oldIndex < oldToNew.length ? oldToNew[oldIndex] : 0;
            // Drop unmapped / out-of-range markers so chips always match Sources 1..n.
            String replacement = mapped > 0 ? "[" + mapped + "]" : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return collapseSpaces(out.toString());
    }

    private static String stripAllCitations(String answer) {
        return collapseSpaces(CITATION.matcher(answer).replaceAll(""));
    }

    private static String collapseSpaces(String value) {
        return value
                .replaceAll(" +([.,;:!?])", "$1")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    record Result(String answer, List<ChatModel.RetrievedChunk> chunks) {
    }
}

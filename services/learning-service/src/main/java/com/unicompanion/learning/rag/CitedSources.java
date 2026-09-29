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
     * If the model cited nothing, falls back to all retrieved chunks (unchanged numbering).
     */
    static Result select(String answer, List<ChatModel.RetrievedChunk> retrieved) {
        if (retrieved == null || retrieved.isEmpty()) {
            return new Result(answer == null ? "" : answer, List.of());
        }
        Set<Integer> cited = firstMentionOrder(answer, retrieved.size());
        if (cited.isEmpty()) {
            return new Result(answer, List.copyOf(retrieved));
        }

        List<ChatModel.RetrievedChunk> selected = new ArrayList<>();
        int[] oldToNew = new int[retrieved.size() + 1];
        int next = 1;
        for (int oldIndex : cited) {
            selected.add(retrieved.get(oldIndex - 1));
            oldToNew[oldIndex] = next++;
        }
        return new Result(rewriteCitations(answer, oldToNew), List.copyOf(selected));
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
            String replacement = mapped > 0 ? "[" + mapped + "]" : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    record Result(String answer, List<ChatModel.RetrievedChunk> chunks) {
    }
}

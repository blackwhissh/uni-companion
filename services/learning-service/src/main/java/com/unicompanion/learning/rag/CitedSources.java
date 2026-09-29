package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps Sources aligned with inline [n] markers in the answer text.
 * Citation numbers refer to retrieved excerpt indices, not PDF page numbers.
 * Never invents citation chips the model did not earn.
 */
final class CitedSources {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");

    private CitedSources() {
    }

    /**
     * Returns chunks referenced as [n] in {@code answer}, renumbered 1..k in first-mention order.
     * Out-of-range markers (often page numbers) are stripped. When nothing valid was cited,
     * returns no source list — the UI should show an ungrounded / no-match state instead of
     * fabricating support.
     */
    static Result select(String answer, List<ChatModel.RetrievedChunk> retrieved) {
        String text = answer == null ? "" : answer;
        if (retrieved == null || retrieved.isEmpty()) {
            return new Result(stripOutOfRangeCitations(text, 0), List.of());
        }

        String cleaned = stripOutOfRangeCitations(text, retrieved.size());
        Set<Integer> cited = firstMentionOrder(cleaned, retrieved.size());
        if (cited.isEmpty()) {
            return new Result(cleaned, List.of());
        }

        List<ChatModel.RetrievedChunk> selected = new ArrayList<>();
        int[] oldToNew = new int[retrieved.size() + 1];
        int next = 1;
        for (int oldIndex : cited) {
            selected.add(retrieved.get(oldIndex - 1));
            oldToNew[oldIndex] = next++;
        }
        return new Result(rewriteCitations(cleaned, oldToNew), List.copyOf(selected));
    }

    private static Set<Integer> firstMentionOrder(String answer, int maxIndex) {
        Set<Integer> ordered = new LinkedHashSet<>();
        if (answer == null || answer.isBlank() || maxIndex < 1) {
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

    private static String stripOutOfRangeCitations(String answer, int maxIndex) {
        Matcher matcher = CITATION.matcher(answer);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String replacement = (index >= 1 && index <= maxIndex) ? matcher.group() : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return tidyCitationGaps(out.toString());
    }

    private static String rewriteCitations(String answer, int[] oldToNew) {
        Matcher matcher = CITATION.matcher(answer);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            int oldIndex = Integer.parseInt(matcher.group(1));
            int mapped = oldIndex < oldToNew.length ? oldToNew[oldIndex] : 0;
            String replacement = mapped > 0 ? "[" + mapped + "]" : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return tidyCitationGaps(out.toString());
    }

    private static String tidyCitationGaps(String value) {
        return value
                .replaceAll(" +([.,;:!?])", "$1")
                .replaceAll(" {2,}", " ")
                .replaceAll(" *\\n", "\n")
                .trim();
    }

    record Result(String answer, List<ChatModel.RetrievedChunk> chunks) {
    }
}

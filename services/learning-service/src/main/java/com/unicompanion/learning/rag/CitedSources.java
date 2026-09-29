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
 */
final class CitedSources {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");
    private static final Pattern DIRECT_ANSWER_HEADING = Pattern.compile(
            "(?m)^##\\s+Direct answer\\s*$"
    );

    private CitedSources() {
    }

    /**
     * Returns chunks referenced as [n] in {@code answer}, renumbered 1..k in first-mention order.
     * Out-of-range markers (often page numbers) are stripped. When the model cites nothing valid,
     * keeps top retrieved sources and injects a grounding chip so students can still verify.
     */
    static Result select(String answer, List<ChatModel.RetrievedChunk> retrieved) {
        String text = answer == null ? "" : answer;
        if (retrieved == null || retrieved.isEmpty()) {
            return new Result(stripOutOfRangeCitations(text, 0), List.of());
        }

        String cleaned = stripOutOfRangeCitations(text, retrieved.size());
        Set<Integer> cited = firstMentionOrder(cleaned, retrieved.size());
        if (cited.isEmpty()) {
            int keep = Math.min(retrieved.size(), 2);
            List<ChatModel.RetrievedChunk> fallback = List.copyOf(retrieved.subList(0, keep));
            return new Result(injectGroundingCitation(cleaned), fallback);
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

    /**
     * Removes only [n] where n is outside 1..maxIndex (e.g. PDF page numbers mistaken for citations).
     * Valid markers are kept so chips remain visible.
     */
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

    /**
     * Ensures the Direct answer has at least one [1] so the UI can show a verifiable chip
     * when the model omitted citations or only emitted page-number brackets.
     */
    private static String injectGroundingCitation(String answer) {
        if (answer == null || answer.isBlank()) {
            return "## Direct answer\nI could not find a grounded citation in the published materials. [1]";
        }
        if (!firstMentionOrder(answer, 1).isEmpty()) {
            return answer;
        }
        Matcher heading = DIRECT_ANSWER_HEADING.matcher(answer);
        if (heading.find()) {
            int afterHeading = heading.end();
            int lineEnd = answer.indexOf('\n', afterHeading);
            if (lineEnd < 0) {
                lineEnd = answer.length();
            }
            // Skip blank lines after the heading.
            int contentStart = afterHeading;
            while (contentStart < answer.length() && answer.charAt(contentStart) == '\n') {
                contentStart++;
            }
            int sentenceEnd = findFirstSentenceEnd(answer, contentStart);
            if (sentenceEnd > contentStart) {
                return answer.substring(0, sentenceEnd)
                        + " [1]"
                        + answer.substring(sentenceEnd);
            }
            return answer.substring(0, contentStart) + "[1] " + answer.substring(contentStart);
        }
        return answer.stripTrailing() + " [1]";
    }

    private static int findFirstSentenceEnd(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                return i;
            }
            if ((ch == '.' || ch == '!' || ch == '?') && (i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1)))) {
                return i + 1;
            }
        }
        int nextBreak = text.indexOf('\n', from);
        return nextBreak >= 0 ? nextBreak : text.length();
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

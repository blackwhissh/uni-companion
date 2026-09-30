package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps Sources aligned with inline [n] markers in the answer text.
 * Citation numbers refer to retrieved excerpt indices, not PDF page numbers.
 * Never invents citation chips the model did not earn — but recovers real
 * grounding when the model used page numbers or omitted markers while the
 * answer text clearly overlaps a retrieved passage.
 */
final class CitedSources {

    /** Single [n] or list forms like [1, 2, 3] / [1-3] / [1;2]. */
    private static final Pattern CITATION_GROUP = Pattern.compile("\\[(\\d+(?:\\s*[,;]\\s*\\d+)*|\\d+\\s*-\\s*\\d+)]");
    private static final Pattern INSUFFICIENT = Pattern.compile(
            "(?i)\\b("
                    + "could not find|cannot find|can't find|not (?:covered|found|present|available)|"
                    + "no (?:relevant|supporting|matching)|insufficient|"
                    + "not (?:in|among) the (?:published |course )?materials|"
                    + "do not appear in|doesn't appear in|does not appear in"
                    + ")\\b"
    );
    private static final Pattern DIRECT_ANSWER_HEADING = Pattern.compile(
            "(?m)^##\\s+(?:Direct answer|Direkte Antwort|Antwort)\\s*$"
    );
    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "for", "is", "are", "was", "were",
            "be", "as", "by", "with", "from", "that", "this", "it", "its", "you", "your", "what",
            "how", "why", "when", "which", "about", "into", "than", "then", "also", "can", "do",
            "does", "did", "will", "would", "should", "could", "not", "no", "yes", "direct", "answer",
            "explanation", "key", "terms"
    );

    private CitedSources() {
    }

    /**
     * Returns chunks referenced as [n] in {@code answer}, renumbered 1..k in first-mention order.
     * List-style markers are expanded; out-of-range markers (often page numbers) are stripped.
     * Insufficient / not-found answers never keep source chips. When nothing valid was cited but
     * the answer overlaps a retrieved passage, that passage is attached with a verified chip.
     */
    static Result select(String answer, List<ChatModel.RetrievedChunk> retrieved) {
        String text = answer == null ? "" : answer;
        if (retrieved == null || retrieved.isEmpty()) {
            return new Result(stripAllCitationGroups(text), List.of());
        }

        if (looksInsufficient(text)) {
            return new Result(stripAllCitationGroups(text), List.of());
        }

        String normalized = expandCitationGroups(text, retrieved.size());
        Set<Integer> cited = firstMentionOrder(normalized, retrieved.size());
        if (cited.isEmpty()) {
            return recoverFromOverlap(normalized, retrieved);
        }

        List<ChatModel.RetrievedChunk> selected = new ArrayList<>();
        int[] oldToNew = new int[retrieved.size() + 1];
        int next = 1;
        for (int oldIndex : cited) {
            selected.add(retrieved.get(oldIndex - 1));
            oldToNew[oldIndex] = next++;
        }
        return new Result(rewriteCitations(normalized, oldToNew), List.copyOf(selected));
    }

    /**
     * When the model omitted excerpt citations (or only used page numbers), attach chunks whose
     * content clearly overlaps the answer — and only then place a [n] chip.
     */
    private static Result recoverFromOverlap(String answer, List<ChatModel.RetrievedChunk> retrieved) {
        Set<String> answerTerms = significantTerms(answer);
        if (answerTerms.size() < 3) {
            return new Result(stripAllCitationGroups(answer), List.of());
        }

        List<Integer> overlapping = new ArrayList<>();
        for (int i = 0; i < retrieved.size(); i++) {
            Set<String> chunkTerms = significantTerms(retrieved.get(i).content());
            if (chunkTerms.isEmpty()) {
                continue;
            }
            long shared = answerTerms.stream().filter(chunkTerms::contains).count();
            double overlap = (double) shared / Math.min(answerTerms.size(), chunkTerms.size());
            if (shared >= 2 && overlap >= 0.28) {
                overlapping.add(i + 1);
            }
        }
        if (overlapping.isEmpty()) {
            return new Result(stripAllCitationGroups(answer), List.of());
        }

        List<ChatModel.RetrievedChunk> selected = overlapping.stream()
                .map(index -> retrieved.get(index - 1))
                .toList();
        String withChip = placeVerifiedCitation(stripAllCitationGroups(answer), 1);
        if (selected.size() == 1) {
            return new Result(withChip, selected);
        }
        // Multiple overlapping passages: chip only the strongest (first) so the UI stays honest.
        return new Result(withChip, List.of(selected.getFirst()));
    }

    private static String placeVerifiedCitation(String answer, int index) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        if (!firstMentionOrder(answer, index).isEmpty()) {
            return answer;
        }
        Matcher heading = DIRECT_ANSWER_HEADING.matcher(answer);
        if (heading.find()) {
            int afterHeading = heading.end();
            int contentStart = afterHeading;
            while (contentStart < answer.length() && answer.charAt(contentStart) == '\n') {
                contentStart++;
            }
            int sentenceEnd = findFirstSentenceEnd(answer, contentStart);
            if (sentenceEnd > contentStart) {
                return answer.substring(0, sentenceEnd)
                        + " [" + index + "]"
                        + answer.substring(sentenceEnd);
            }
            return answer.substring(0, contentStart) + "[" + index + "] " + answer.substring(contentStart);
        }
        return answer.stripTrailing() + " [" + index + "]";
    }

    private static int findFirstSentenceEnd(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                return i;
            }
            if ((ch == '.' || ch == '!' || ch == '?')
                    && (i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1)))) {
                return i + 1;
            }
        }
        int nextBreak = text.indexOf('\n', from);
        return nextBreak >= 0 ? nextBreak : text.length();
    }

    private static boolean looksInsufficient(String answer) {
        return answer != null && INSUFFICIENT.matcher(answer).find();
    }

    /**
     * Turns {@code [1, 2, 6]} / {@code [2-4]} into individual {@code [n]} markers that fall in range;
     * drops out-of-range numbers (page numbers, phantom indexes).
     */
    private static String expandCitationGroups(String answer, int maxIndex) {
        Matcher matcher = CITATION_GROUP.matcher(answer);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            List<Integer> numbers = parseGroup(matcher.group(1));
            StringBuilder replacement = new StringBuilder();
            Set<Integer> seen = new LinkedHashSet<>();
            for (int number : numbers) {
                if (number >= 1 && number <= maxIndex && seen.add(number)) {
                    if (!replacement.isEmpty()) {
                        replacement.append(' ');
                    }
                    replacement.append('[').append(number).append(']');
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(out);
        return tidyCitationGaps(out.toString());
    }

    private static String stripAllCitationGroups(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        String stripped = CITATION_GROUP.matcher(answer).replaceAll("");
        return tidyCitationGaps(stripped);
    }

    private static List<Integer> parseGroup(String body) {
        List<Integer> numbers = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return numbers;
        }
        String trimmed = body.trim();
        if (trimmed.contains("-") && !trimmed.contains(",") && !trimmed.contains(";")) {
            String[] ends = trimmed.split("-", 2);
            if (ends.length == 2) {
                try {
                    int start = Integer.parseInt(ends[0].trim());
                    int end = Integer.parseInt(ends[1].trim());
                    if (start > end) {
                        int swap = start;
                        start = end;
                        end = swap;
                    }
                    // Cap range expansion so a typo like [1-99] cannot flood markers.
                    int limit = Math.min(end, start + 20);
                    for (int n = start; n <= limit; n++) {
                        numbers.add(n);
                    }
                    return numbers;
                } catch (NumberFormatException ignored) {
                    return numbers;
                }
            }
        }
        for (String part : trimmed.split("[,;]")) {
            try {
                numbers.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return numbers;
    }

    private static Set<Integer> firstMentionOrder(String answer, int maxIndex) {
        Set<Integer> ordered = new LinkedHashSet<>();
        if (answer == null || answer.isBlank() || maxIndex < 1) {
            return ordered;
        }
        Matcher matcher = CITATION_GROUP.matcher(answer);
        while (matcher.find()) {
            for (int index : parseGroup(matcher.group(1))) {
                if (index >= 1 && index <= maxIndex) {
                    ordered.add(index);
                }
            }
        }
        return ordered;
    }

    private static String rewriteCitations(String answer, int[] oldToNew) {
        Matcher matcher = CITATION_GROUP.matcher(answer);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            List<Integer> numbers = parseGroup(matcher.group(1));
            StringBuilder replacement = new StringBuilder();
            Set<Integer> seen = new LinkedHashSet<>();
            for (int oldIndex : numbers) {
                int mapped = oldIndex < oldToNew.length ? oldToNew[oldIndex] : 0;
                if (mapped > 0 && seen.add(mapped)) {
                    if (!replacement.isEmpty()) {
                        replacement.append(' ');
                    }
                    replacement.append('[').append(mapped).append(']');
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(out);
        return tidyCitationGaps(out.toString());
    }

    private static Set<String> significantTerms(String text) {
        Set<String> out = new HashSet<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.isEmpty()) {
                continue;
            }
            if (raw.chars().allMatch(Character::isDigit)) {
                out.add(raw);
                continue;
            }
            if (raw.length() < 3 || STOP.contains(raw)) {
                continue;
            }
            out.add(raw);
        }
        return out;
    }

    private static String tidyCitationGaps(String value) {
        return value
                .replaceAll(" +([.,;:!?])", "$1")
                .replaceAll(" {2,}", " ")
                .replaceAll(" *\\n", "\n")
                // Stray punctuation left alone after a chip/list strip (e.g. ".\n" after [1]).
                .replaceAll("(?m)^\\s*[.,;:]+\\s*$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    record Result(String answer, List<ChatModel.RetrievedChunk> chunks) {
    }
}

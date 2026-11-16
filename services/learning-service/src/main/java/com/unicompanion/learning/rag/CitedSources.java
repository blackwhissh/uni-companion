package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                    + "do not appear in|doesn't appear in|does not appear in|"
                    // German
                    + "konnte(?:\\s+\\w+){0,4}\\s+nicht(?:s)?\\s+finden|kann(?:\\s+\\w+){0,4}\\s+nicht(?:s)?\\s+finden|"
                    + "nichts\\s+(?:gefunden|finden|in\\s+den)|"
                    + "nicht\\s+(?:gefunden|abgedeckt|enthalten|vorhanden|verfügbar)|"
                    + "keine\\s+(?:relevanten|passenden|unterstützenden)|"
                    + "unzureichend|"
                    + "nicht\\s+in\\s+den\\s+(?:veröffentlichten\\s+|kurs-?)?materialien|"
                    + "erscheint\\s+nicht\\s+in|kommen\\s+nicht\\s+vor|kommt\\s+nicht\\s+vor|"
                    + "lassen\\s+sich\\s+nicht\\s+(?:finden|belegen)"
                    + ")\\b"
    );
    private static final Pattern DIRECT_ANSWER_HEADING = Pattern.compile(
            "(?m)^##\\s+(?:Direct answer|Direkte Antwort|Antwort)\\s*$"
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
        String directAnswer = directAnswerSection(normalized);
        if (looksInsufficient(directAnswer)) {
            return new Result(stripAllCitationGroups(normalized), List.of());
        }

        normalized = rebindMismatchedCitations(normalized, retrieved);
        // When some claims already have chips, still attach missing Direct-answer cites
        // (recoverFromOverlap only runs when every marker was stripped).
        normalized = addMissingDirectAnswerCitations(normalized, retrieved);
        Set<Integer> cited = firstMentionOrder(normalized, retrieved.size());
        if (cited.isEmpty()) {
            return recoverFromOverlap(normalized, retrieved);
        }

        LinkedHashSet<Integer> aligned = alignCitations(normalized, retrieved, cited);
        List<ChatModel.RetrievedChunk> selected = new ArrayList<>();
        int[] oldToNew = new int[retrieved.size() + 1];
        int next = 1;
        for (int oldIndex : aligned) {
            selected.add(retrieved.get(oldIndex - 1));
            oldToNew[oldIndex] = next++;
        }
        String remapped = remapCitationIndexes(normalized, cited, aligned);
        return new Result(rewriteCitations(remapped, oldToNew), List.copyOf(selected));
    }

    /**
     * When the model omitted excerpt citations (or only used page numbers), attach chunks whose
     * content clearly overlaps each Direct-answer claim sentence — one chip per grounded claim.
     */
    private static Result recoverFromOverlap(String answer, List<ChatModel.RetrievedChunk> retrieved) {
        String stripped = stripAllCitationGroups(answer);
        List<SentenceSpan> claims = directAnswerSentences(stripped);
        if (claims.isEmpty()) {
            claims = List.of(new SentenceSpan(0, stripped.length(), stripped.trim()));
        }

        Map<Integer, Integer> claimToChunk = new LinkedHashMap<>();
        LinkedHashSet<Integer> selectedIndexes = new LinkedHashSet<>();
        for (int i = 0; i < claims.size(); i++) {
            int best = bestMatchingChunk(claims.get(i).text(), retrieved, 2, 0.28);
            if (best > 0) {
                claimToChunk.put(i, best);
                selectedIndexes.add(best);
            }
        }
        if (selectedIndexes.isEmpty()) {
            return new Result(stripped, List.of());
        }

        List<Integer> indexOrder = new ArrayList<>(selectedIndexes);
        int[] chunkToNew = new int[retrieved.size() + 1];
        List<ChatModel.RetrievedChunk> selected = new ArrayList<>();
        int next = 1;
        for (int oldIndex : indexOrder) {
            chunkToNew[oldIndex] = next++;
            selected.add(retrieved.get(oldIndex - 1));
        }

        String withChips = placeClaimCitations(stripped, claims, claimToChunk, chunkToNew);
        return new Result(withChips, List.copyOf(selected));
    }

    /**
     * Rebind [n] markers whose surrounding claim does not overlap the cited chunk onto a better
     * retrieved passage (or strip them) so chips cannot point at unrelated quotes.
     */
    private static String rebindMismatchedCitations(String answer, List<ChatModel.RetrievedChunk> retrieved) {
        Matcher matcher = CITATION_GROUP.matcher(answer);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            List<Integer> numbers = parseGroup(matcher.group(1));
            String claim = sentenceAround(answer, matcher.start(), matcher.end());
            StringBuilder replacement = new StringBuilder();
            Set<Integer> seen = new LinkedHashSet<>();
            for (int number : numbers) {
                int bound = number;
                if (number >= 1 && number <= retrieved.size()) {
                    // Only rebind when the cited chunk clearly fails and another chunk fits better.
                    // Never strip a in-range model cite just because lexical overlap is weak —
                    // short chunks like "delta" will not match the surrounding prose.
                    if (!supportsClaim(claim, retrieved.get(number - 1).content())) {
                        int rebound = bestMatchingChunk(claim, retrieved, 2, 0.22);
                        if (rebound > 0 && rebound != number) {
                            bound = rebound;
                        }
                    }
                } else {
                    bound = 0;
                }
                if (bound > 0 && seen.add(bound)) {
                    if (!replacement.isEmpty()) {
                        replacement.append(' ');
                    }
                    replacement.append('[').append(bound).append(']');
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(out);
        return tidyCitationGaps(out.toString());
    }

    /**
     * Adds [n] chips to Direct-answer sentences that are grounded in a retrieved passage but
     * were left uncited (e.g. second claim in a two-part sorting question).
     */
    private static String addMissingDirectAnswerCitations(String answer, List<ChatModel.RetrievedChunk> retrieved) {
        List<SentenceSpan> claims = directAnswerSentences(answer);
        if (claims.isEmpty()) {
            return answer;
        }
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (SentenceSpan claim : claims) {
            out.append(answer, cursor, claim.end());
            if (firstMentionOrder(claim.text(), retrieved.size()).isEmpty()) {
                int best = bestMatchingChunk(claim.text(), retrieved, 2, 0.22);
                if (best > 0) {
                    out.append(" [").append(best).append(']');
                }
            }
            cursor = claim.end();
        }
        if (cursor < answer.length()) {
            out.append(answer.substring(cursor));
        }
        return tidyCitationGaps(out.toString());
    }

    private static String placeClaimCitations(
            String answer,
            List<SentenceSpan> claims,
            Map<Integer, Integer> claimToChunk,
            int[] chunkToNew
    ) {
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (int i = 0; i < claims.size(); i++) {
            SentenceSpan claim = claims.get(i);
            out.append(answer, cursor, claim.end());
            Integer oldIndex = claimToChunk.get(i);
            if (oldIndex != null) {
                int mapped = chunkToNew[oldIndex];
                if (mapped > 0 && firstMentionOrder(claim.text(), mapped).isEmpty()) {
                    out.append(" [").append(mapped).append(']');
                }
            }
            cursor = claim.end();
        }
        if (cursor < answer.length()) {
            out.append(answer.substring(cursor));
        }
        return tidyCitationGaps(out.toString());
    }

    private static int bestMatchingChunk(
            String claim,
            List<ChatModel.RetrievedChunk> retrieved,
            int minShared,
            double minOverlap
    ) {
        Set<String> claimTerms = significantTerms(claim);
        if (claimTerms.size() < 2) {
            return 0;
        }
        int bestIndex = 0;
        double bestScore = 0;
        for (int i = 0; i < retrieved.size(); i++) {
            Set<String> chunkTerms = significantTerms(retrieved.get(i).content());
            if (chunkTerms.isEmpty()) {
                continue;
            }
            long shared = claimTerms.stream().filter(chunkTerms::contains).count();
            double overlap = (double) shared / Math.min(claimTerms.size(), chunkTerms.size());
            if (shared < minShared || overlap < minOverlap) {
                continue;
            }
            double score = shared + overlap;
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i + 1;
            }
        }
        return bestIndex;
    }

    private static LinkedHashSet<Integer> alignCitations(
            String answer,
            List<ChatModel.RetrievedChunk> retrieved,
            Set<Integer> cited
    ) {
        // Judge each chip against the sentence that contains it. The whole Direct answer
        // often states two facts, and one page cannot contain both.
        LinkedHashSet<Integer> aligned = new LinkedHashSet<>();
        Matcher matcher = CITATION_GROUP.matcher(answer);
        while (matcher.find()) {
            String claim = sentenceAround(answer, matcher.start(), matcher.end());
            for (int index : parseGroup(matcher.group(1))) {
                if (!cited.contains(index) || index < 1 || index > retrieved.size()) {
                    continue;
                }
                int chosen = index;
                if (!chunkSupportsClaim(retrieved.get(index - 1).content(), claim)) {
                    int replacement = bestSupportingChunk(retrieved, claim, aligned);
                    if (replacement > 0) {
                        chosen = replacement;
                    }
                }
                aligned.add(chosen);
            }
        }
        return aligned.isEmpty() ? new LinkedHashSet<>(cited) : aligned;
    }

    private static String remapCitationIndexes(String answer, Set<Integer> cited, LinkedHashSet<Integer> aligned) {
        if (cited.size() != aligned.size()) {
            return answer;
        }
        List<Integer> from = new ArrayList<>(cited);
        List<Integer> to = new ArrayList<>(aligned);
        String updated = answer;
        for (int i = 0; i < from.size(); i++) {
            int oldIndex = from.get(i);
            int newIndex = to.get(i);
            if (oldIndex != newIndex) {
                updated = updated.replace("[" + oldIndex + "]", "«" + newIndex + "»");
            }
        }
        for (int newIndex : to) {
            updated = updated.replace("«" + newIndex + "»", "[" + newIndex + "]");
        }
        return updated;
    }

    private static String directAnswerSection(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        Matcher heading = DIRECT_ANSWER_HEADING.matcher(answer);
        if (!heading.find()) {
            return answer;
        }
        int start = heading.end();
        while (start < answer.length() && answer.charAt(start) == '\n') {
            start++;
        }
        Matcher next = Pattern.compile("(?m)^##\\s+").matcher(answer);
        next.region(start, answer.length());
        int end = next.find() ? next.start() : answer.length();
        return answer.substring(start, end).trim();
    }

    private static boolean supportsClaim(String claim, String chunkContent) {
        return chunkSupportsClaim(chunkContent, claim);
    }

    private static boolean chunkSupportsClaim(String chunkContent, String claim) {
        if (claim == null || claim.isBlank()) {
            return true;
        }
        Set<String> claimTerms = significantTerms(claim);
        if (claimTerms.isEmpty()) {
            return true;
        }
        String normalizedChunk = chunkContent == null ? "" : chunkContent.toLowerCase(Locale.ROOT);
        for (String digit : claimTerms) {
            if (digit.chars().allMatch(Character::isDigit) && !normalizedChunk.contains(digit)) {
                return false;
            }
        }
        for (String term : claimTerms) {
            if (term.length() >= 5 && !normalizedChunk.contains(term)) {
                return false;
            }
        }
        Set<String> chunkTerms = significantTerms(chunkContent);
        long shared = claimTerms.stream().filter(chunkTerms::contains).count();
        double overlap = (double) shared / Math.min(claimTerms.size(), Math.max(1, chunkTerms.size()));
        return shared >= 2 && overlap >= 0.22;
    }

    private static int bestSupportingChunk(
            List<ChatModel.RetrievedChunk> retrieved,
            String claim,
            Set<Integer> alreadyUsed
    ) {
        int bestIndex = -1;
        int bestScore = -1;
        for (int i = 0; i < retrieved.size(); i++) {
            int index = i + 1;
            if (alreadyUsed.contains(index)) {
                continue;
            }
            int score = overlapScore(claim, retrieved.get(i).content());
            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        return bestScore >= 2 ? bestIndex : -1;
    }

    private static int overlapScore(String left, String right) {
        Set<String> leftTerms = significantTerms(left);
        Set<String> rightTerms = significantTerms(right);
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) {
            return 0;
        }
        long shared = leftTerms.stream().filter(rightTerms::contains).count();
        return (int) shared;
    }

    private static List<SentenceSpan> directAnswerSentences(String answer) {
        Matcher heading = DIRECT_ANSWER_HEADING.matcher(answer);
        if (!heading.find()) {
            return List.of();
        }
        int contentStart = heading.end();
        while (contentStart < answer.length() && answer.charAt(contentStart) == '\n') {
            contentStart++;
        }
        int contentEnd = answer.length();
        Matcher nextHeading = Pattern.compile("(?m)^##\\s+").matcher(answer);
        if (nextHeading.find(contentStart)) {
            contentEnd = nextHeading.start();
        }
        String body = answer.substring(contentStart, contentEnd);
        List<SentenceSpan> spans = new ArrayList<>();
        int local = 0;
        while (local < body.length()) {
            while (local < body.length() && Character.isWhitespace(body.charAt(local))) {
                local++;
            }
            if (local >= body.length()) {
                break;
            }
            int endLocal = findFirstSentenceEnd(body, local);
            String text = body.substring(local, endLocal).trim();
            if (!text.isEmpty()) {
                spans.add(new SentenceSpan(contentStart + local, contentStart + endLocal, text));
            }
            local = endLocal;
        }
        return spans;
    }

    private static String sentenceAround(String answer, int markerStart, int markerEnd) {
        int start = markerStart;
        while (start > 0 && answer.charAt(start - 1) != '\n') {
            start--;
        }
        int end = markerEnd;
        while (end < answer.length() && answer.charAt(end) != '\n') {
            end++;
        }
        String line = answer.substring(start, end);
        return CITATION_GROUP.matcher(line).replaceAll("").trim();
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
        return LexicalTerms.significant(text);
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

    private record SentenceSpan(int start, int end, String text) {
    }

    record Result(String answer, List<ChatModel.RetrievedChunk> chunks) {
    }
}

package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns raw chunk text into a short section label + concrete quote for the Sources UI.
 */
final class CitationText {

    private static final Pattern CHAPTER = Pattern.compile(
            "(?i)\\b(Chapter\\s+\\d+\\s*[—–\\-]\\s*[^\\n.!?]{3,100}[?!]?)"
    );
    private static final Pattern STUDY_PREFIX = Pattern.compile(
            "(?i)^(?:[\\w\\s]+—\\s*)?Study Material\\s+\\d+\\s+"
    );
    private static final Pattern CHECKLIST = Pattern.compile(
            "(?i)^(can you|•|\\u2022|[-*]\\s|final revision|checklist|foundations)\\b"
    );
    private static final Pattern COMPARISON_HEADER = Pattern.compile(
            "(?i)\\bProperty\\s+Centralized\\s+Distributed\\b"
    );
    private static final Pattern TWO_CELLS = Pattern.compile(
            "^(.+?)\\s+([A-ZÀ-ÖØ-Þ0-9].*)$"
    );
    private static final Pattern DIRECT_ANSWER_SECTION = Pattern.compile(
            "(?is)##\\s*(?:Direct answer|Direkte Antwort|Antwort)\\s*(.*?)(?=##\\s|$)"
    );
    /**
     * Longer labels first so "Failure scope" wins over "Failure".
     */
    private static final String[] TABLE_ROW_LABELS = {
            "Failure scope",
            "Geographic distribution",
            "Communication",
            "Coordination",
            "Consistency",
            "Availability",
            "Specialization",
            "Isolation",
            "Scaling",
            "State"
    };
    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "for", "is", "are", "was", "were",
            "be", "as", "by", "with", "from", "that", "this", "it", "its", "you", "your", "what",
            "how", "why", "when", "which", "about", "into", "than", "then", "also", "can", "do",
            "does", "did", "will", "would", "should", "could", "not", "no", "yes"
    );
    private static final int EXCERPT_MAX = 420;

    private CitationText() {
    }

    static String sectionHint(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher matcher = CHAPTER.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim().replaceAll("\\s+", " ");
        }
        return null;
    }

    static String excerpt(String content) {
        return excerpt(content, null, null);
    }

    /**
     * Prefers concrete prose sentences; reformats flattened PDF comparison tables into readable rows.
     */
    static String excerpt(String content, String question, String answer) {
        String body = cleanBody(content);
        if (body.isEmpty()) {
            return body;
        }

        Set<String> focus = tokens(question);
        // Prefer overlap with the Direct answer claim so the quote matches what the student sees first.
        focus.addAll(tokens(directAnswerBody(answer)));
        focus.addAll(tokens(answer));

        String prose = pickProse(body, focus);
        if (prose != null) {
            return prose;
        }

        String table = formatComparisonTable(body);
        if (table != null) {
            return table;
        }

        List<String> sentences = splitSentences(body);
        if (!sentences.isEmpty()) {
            return trimToMax(sentences.getFirst());
        }
        return trimToMax(body);
    }

    private static String directAnswerBody(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        Matcher matcher = DIRECT_ANSWER_SECTION.matcher(answer);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return answer;
    }

    private static String pickProse(String body, Set<String> focus) {
        List<String> sentences = splitSentences(body).stream()
                .filter(sentence -> !looksLikeTableDump(sentence))
                .filter(sentence -> !CHECKLIST.matcher(sentence).find())
                .toList();
        if (sentences.isEmpty()) {
            return null;
        }

        List<Scored> ranked = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            ranked.add(new Scored(i, sentences.get(i), score(sentences.get(i), focus)));
        }
        ranked.sort(Comparator.comparingInt(Scored::score).reversed().thenComparingInt(Scored::index));
        Scored best = ranked.getFirst();
        if (best.score() < 3 && looksLikeTableDump(body)) {
            // Weak prose while the chunk is mostly a table — prefer the table formatter.
            return null;
        }

        StringBuilder out = new StringBuilder(best.text());
        int bestIndex = best.index();
        if (bestIndex + 1 < sentences.size()) {
            String next = sentences.get(bestIndex + 1);
            if (!looksLikeTableDump(next)
                    && !CHECKLIST.matcher(next).find()
                    && score(next, focus) >= 2
                    && out.length() + 1 + next.length() <= EXCERPT_MAX) {
                out.append(' ').append(next);
            }
        }
        return trimToMax(out.toString());
    }

    static String formatComparisonTable(String body) {
        Matcher header = COMPARISON_HEADER.matcher(body);
        if (!header.find()) {
            return null;
        }
        String rest = body.substring(header.end()).trim();
        if (rest.isEmpty()) {
            return null;
        }

        List<LabelHit> hits = findRowLabels(rest);
        if (hits.size() < 2) {
            return null;
        }

        List<String> rows = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            LabelHit hit = hits.get(i);
            int valueStart = hit.end();
            int valueEnd = i + 1 < hits.size() ? hits.get(i + 1).start() : rest.length();
            String values = rest.substring(valueStart, valueEnd).trim();
            Matcher cells = TWO_CELLS.matcher(values);
            if (!cells.matches()) {
                continue;
            }
            String centralized = cells.group(1).trim();
            String distributed = cells.group(2).trim();
            rows.add("%s: %s → %s".formatted(hit.label(), centralized, distributed));
        }
        if (rows.isEmpty()) {
            return null;
        }
        return trimToMax("Centralized vs distributed — " + String.join("; ", rows));
    }

    private static List<LabelHit> findRowLabels(String rest) {
        List<LabelHit> hits = new ArrayList<>();
        for (String label : TABLE_ROW_LABELS) {
            Pattern pattern = Pattern.compile("(?i)(?<!\\w)" + Pattern.quote(label) + "(?!\\w)");
            Matcher matcher = pattern.matcher(rest);
            if (matcher.find()) {
                hits.add(new LabelHit(label, matcher.start(), matcher.end()));
            }
        }
        hits.sort(Comparator.comparingInt(LabelHit::start));
        return hits;
    }

    private static boolean looksLikeTableDump(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (COMPARISON_HEADER.matcher(text).find()) {
            return true;
        }
        long periods = text.chars().filter(ch -> ch == '.' || ch == '?' || ch == '!').count();
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        return periods == 0 && words >= 18;
    }

    private static String cleanBody(String content) {
        String value = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            return value;
        }
        Matcher chapter = CHAPTER.matcher(value);
        if (chapter.find()) {
            int after = chapter.end();
            while (after < value.length() && Character.isWhitespace(value.charAt(after))) {
                after++;
            }
            if (after < value.length()) {
                value = value.substring(after);
            }
        } else {
            value = STUDY_PREFIX.matcher(value).replaceFirst("");
        }
        value = value.replaceFirst("^[,:;.\\-–—\\s]+", "").trim();
        return value;
    }

    private static List<String> splitSentences(String body) {
        List<String> sentences = new ArrayList<>();
        for (String part : body.split("(?<=[.!?…])\\s+")) {
            String sentence = part.trim();
            if (sentence.length() >= 20) {
                sentences.add(sentence);
            }
        }
        if (sentences.isEmpty() && body.length() >= 20) {
            sentences.add(body);
        }
        return sentences;
    }

    private static int score(String sentence, Set<String> focus) {
        int score = 0;
        Set<String> words = tokens(sentence);
        for (String word : words) {
            if (focus.contains(word)) {
                score += 3;
            }
        }
        String lower = sentence.toLowerCase(Locale.ROOT);
        if (lower.contains(" is a ") || lower.contains(" are ") || lower.contains(" means ")
                || lower.contains(" defined as ") || lower.contains(" refers to ")
                || lower.contains(" always ") || lower.contains(" when ")
                || lower.contains(" used for ") || lower.contains(" used in ")) {
            score += 4;
        }
        if (lower.contains(" for example ") || lower.contains(" such as ")) {
            score += 1;
        }
        if (sentence.length() >= 80 && sentence.length() <= 280) {
            score += 2;
        }
        if (looksLikeTableDump(sentence)) {
            score -= 12;
        }
        return score;
    }

    private static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.isEmpty()) {
                continue;
            }
            // Keep numbers (e.g. 17) and short technical tokens so excerpts match the claim.
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

    private static String trimToMax(String value) {
        if (value.length() <= EXCERPT_MAX) {
            return value;
        }
        int cut = value.lastIndexOf(' ', EXCERPT_MAX - 1);
        if (cut < EXCERPT_MAX / 2) {
            cut = EXCERPT_MAX - 1;
        }
        return value.substring(0, cut).trim() + "…";
    }

    private record Scored(int index, String text, int score) {
    }

    private record LabelHit(String label, int start, int end) {
    }
}

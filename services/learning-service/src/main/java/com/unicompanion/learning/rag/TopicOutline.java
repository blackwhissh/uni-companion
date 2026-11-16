package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Groups lecture pages into topics from headings already in the PDF.
 * A page stays in the current topic until a section slide or a chapter line starts the next one.
 */
final class TopicOutline {

    private static final Pattern CHAPTER = Pattern.compile(
            "(?i)^(chapter|section|part|lecture|kapitel|abschnitt|vorlesung)\\b.+"
    );
    private static final Pattern NUMBERED = Pattern.compile(
            "^\\d{1,2}(?:\\.\\d{1,2}){0,2}[.)]?\\s+\\p{L}.+"
    );
    private static final int HEADING_MAX = 90;

    private TopicOutline() {
    }

    static List<String> topics(List<String> pageTexts) {
        List<String> topics = new ArrayList<>(pageTexts.size());
        String current = "Introduction";
        for (String text : pageTexts) {
            String heading = heading(text);
            if (heading != null) {
                current = heading;
            }
            topics.add(current);
        }
        return List.copyOf(topics);
    }

    /**
     * The section title this page opens, or null when the page continues the previous section.
     */
    static String heading(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] lines = nonEmptyLines(text);
        if (lines.length == 0) {
            return null;
        }
        String first = lines[0];
        if (first.length() < 3 || first.length() > HEADING_MAX) {
            return null;
        }
        if (CHAPTER.matcher(first).matches() || numberedHeading(first)) {
            return first;
        }
        if (isTitleSlide(lines, text.trim())) {
            return first;
        }
        return null;
    }

    private static boolean numberedHeading(String first) {
        return first.length() <= 80 && NUMBERED.matcher(first).matches();
    }

    private static boolean isTitleSlide(String[] lines, String trimmed) {
        if (lines.length > 4 || trimmed.length() > 180) {
            return false;
        }
        for (String line : lines) {
            if (line.endsWith(".")) {
                return false;
            }
        }
        return true;
    }

    private static String[] nonEmptyLines(String text) {
        return text.trim().lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toArray(String[]::new);
    }
}

package com.unicompanion.learning.rag;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lightweight question language hint for grounded-answer prompts (no external deps).
 */
final class QuestionLanguage {

    private static final Pattern GERMAN_HINT = Pattern.compile(
            "(?i)\\b(und|oder|der|die|das|ein|eine|was|wie|warum|wann|welche|welcher|welches|nicht|auch|für|bei|nach)\\b"
    );
    private static final Set<Character> GERMAN_CHARS = Set.of('ä', 'ö', 'ü', 'ß');

    private QuestionLanguage() {
    }

    static String instructionLine(String question) {
        if (isLikelyGerman(question)) {
            return "Detected student question language: German. Write the entire answer (all headings and body text) in German.";
        }
        return "Detected student question language: English. Write the entire answer (all headings and body text) in English.";
    }

    static boolean isLikelyGerman(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String trimmed = question.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = Character.toLowerCase(trimmed.charAt(i));
            if (GERMAN_CHARS.contains(ch)) {
                return true;
            }
        }
        long germanHints = GERMAN_HINT.matcher(trimmed.toLowerCase(Locale.ROOT)).results().count();
        return germanHints >= 2;
    }
}

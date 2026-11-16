package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared lexical tokenization for citation overlap / excerpt scoring.
 * Keeps German letters (äöüß) so DE claims match chunk terms the same way the web UI does.
 */
final class LexicalTerms {

    private static final Set<String> STOP = Set.of(
            // English
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "for", "is", "are", "was", "were",
            "be", "as", "by", "with", "from", "that", "this", "it", "its", "you", "your", "what",
            "how", "why", "when", "which", "about", "into", "than", "then", "also", "can", "do",
            "does", "did", "will", "would", "should", "could", "not", "no", "yes", "direct", "answer",
            "explanation", "key", "terms",
            // German (aligned with QaSources.tsx; skip words already listed in English)
            "der", "die", "das", "und", "oder", "ein", "eine", "einer", "einem", "eines", "ist",
            "sind", "nicht", "mit", "auf", "für", "den", "dem", "des", "im", "am", "zum", "zur",
            "vom", "bei", "nach", "über", "unter", "auch", "nur", "noch", "wie", "wer", "wenn",
            "dann", "sich", "man", "wir", "ihr", "sie", "ich", "du", "es", "als", "aus", "durch",
            "gegen", "ohne", "um", "vor", "zwischen", "diese", "dieser", "dieses", "antwort",
            "erklärung", "begriffe"
    );

    private LexicalTerms() {
    }

    static Set<String> significant(String text) {
        Set<String> out = new HashSet<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9äöüß]+")) {
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

    /**
     * Builds a Postgres {@code simple} tsquery of OR'd significant terms (capped).
     * Returns null when the question has no usable terms.
     */
    static String toSimpleFtsQuery(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9äöüß]+")) {
            if (raw.isEmpty()) {
                continue;
            }
            if (raw.chars().allMatch(Character::isDigit)) {
                if (raw.length() <= 6) {
                    terms.add(raw);
                }
                continue;
            }
            if (raw.length() < 3 || STOP.contains(raw)) {
                continue;
            }
            // tsquery tokens: keep ASCII alnum; umlauts are fine in simple config.
            terms.add(raw);
            if (terms.size() >= 12) {
                break;
            }
        }
        if (terms.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>(terms.size());
        for (String term : terms) {
            parts.add(term.replace("'", "''"));
        }
        return String.join(" | ", parts);
    }
}

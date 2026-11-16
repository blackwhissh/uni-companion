package com.unicompanion.learning.study.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds flashcards the student marked "Again" onto the quiz gap list.
 * A card whose front is already a quiz gap is not duplicated.
 */
public final class CardGaps {

    private CardGaps() {
    }

    public static List<ProgressSummary.WeakTopic> merge(
            List<ProgressSummary.WeakTopic> quizGaps,
            List<String> againFronts,
            Instant markedAt
    ) {
        List<ProgressSummary.WeakTopic> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (quizGaps != null) {
            for (ProgressSummary.WeakTopic gap : quizGaps) {
                if (gap == null || gap.prompt() == null || gap.prompt().isBlank() || !seen.add(gap.prompt())) {
                    continue;
                }
                merged.add(gap);
            }
        }
        if (againFronts != null) {
            for (String front : againFronts) {
                String prompt = front == null ? "" : front.trim();
                if (prompt.isEmpty() || !seen.add(prompt)) {
                    continue;
                }
                merged.add(new ProgressSummary.WeakTopic(prompt, 1, markedAt));
            }
        }
        if (merged.size() > ProgressSummary.MAX_WEAK_TOPICS) {
            return List.copyOf(merged.subList(0, ProgressSummary.MAX_WEAK_TOPICS));
        }
        return List.copyOf(merged);
    }
}

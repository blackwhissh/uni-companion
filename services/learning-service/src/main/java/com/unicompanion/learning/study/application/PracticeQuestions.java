package com.unicompanion.learning.study.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the newest stored quiz question for each prompt the student still misses.
 */
public final class PracticeQuestions {

    private PracticeQuestions() {
    }

    public static List<StoredQuestion> pick(List<String> weakPrompts, List<StoredQuestion> newestFirst) {
        Map<String, StoredQuestion> byPrompt = new LinkedHashMap<>();
        if (newestFirst != null) {
            for (StoredQuestion question : newestFirst) {
                String key = normalize(question == null ? null : question.prompt());
                if (key.isEmpty() || question == null) {
                    continue;
                }
                byPrompt.putIfAbsent(key, question);
            }
        }
        List<StoredQuestion> picked = new ArrayList<>();
        if (weakPrompts == null) {
            return picked;
        }
        for (String prompt : weakPrompts) {
            StoredQuestion match = byPrompt.get(normalize(prompt));
            if (match != null) {
                picked.add(match);
            }
        }
        return List.copyOf(picked);
    }

    private static String normalize(String prompt) {
        return prompt == null ? "" : prompt.trim();
    }

    public record StoredQuestion(String prompt, String optionsJson, int correctIndex, String explanation) {
    }
}

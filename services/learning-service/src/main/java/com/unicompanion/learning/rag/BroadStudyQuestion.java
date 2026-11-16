package com.unicompanion.learning.rag;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A question that asks to be taught the lecture, without naming one fact.
 * Those questions should be answered from one section, not from a handful of unrelated pages.
 */
final class BroadStudyQuestion {

    private static final Pattern BROAD = Pattern.compile(
            "(?i)("
                    + "walk me through|step by step|core process|main idea|"
                    + "summarize|summarise|overview|how does this work|explain this lecture|"
                    + "schritt für schritt|kernprozess|hauptidee|zusammenfass|überblick|"
                    + "wie funktioniert|erkläre diese vorlesung"
                    + ")"
    );

    private BroadStudyQuestion() {
    }

    static boolean matches(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return BROAD.matcher(question.toLowerCase(Locale.ROOT)).find();
    }
}

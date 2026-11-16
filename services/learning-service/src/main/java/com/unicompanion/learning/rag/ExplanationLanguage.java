package com.unicompanion.learning.rag;

/**
 * Language of a grounded explanation. Facts stay in the published pages;
 * only the explanation around them is written in this language.
 */
public enum ExplanationLanguage {
    EN,
    DE;

    public String noMaterialAnswer() {
        if (this == DE) {
            return """
                    ## Direkte Antwort
                    Ich konnte das in den Vorlesungen, die du ausgewählt hast, nicht finden.

                    ## Erklärung
                    Frag nach etwas, das diese Vorlesungen erklären, oder wähle eine andere Vorlesung.
                    """;
        }
        return """
                ## Direct answer
                I could not find that in the lectures you picked.

                ## Explanation
                Ask about something those lectures explain, or pick a different lecture.
                """;
    }

    public String promptLine() {
        if (this == DE) {
            return """
                    Write the entire answer in German, including every heading and sentence.
                    Use these headings: ## Direkte Antwort / ## Erklärung / ## Schlüsselbegriffe.
                    Direkte Antwort is the result the student should be able to repeat.
                    Erklärung walks through why that result is true. Its last line starts with **Ergebnis:** and restates the result.
                    The question may be in another language. Keep every fact grounded in the pages.""";
        }
        return """
                Write the entire answer in English, including every heading and sentence.
                Use these headings: ## Direct answer / ## Explanation / ## Key terms.
                Direct answer is the result the student should be able to repeat.
                Explanation walks through why that result is true. Its last line starts with **Result:** and restates the result.
                The question may be in another language. Keep every fact grounded in the pages.""";
    }

    public String summaryPromptLine() {
        if (this == DE) {
            return "Write the summary in German. The lecture pages may be in another language. Translate the explanation and keep every fact grounded in those pages.";
        }
        return "Write the summary in English. The lecture pages may be in another language. Keep every fact grounded in those pages.";
    }
}

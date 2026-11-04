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
                    Ich konnte in den veröffentlichten Materialien nichts Passendes zu dieser Frage finden.

                    ## Erklärung
                    Formuliere die Frage anders, oder frage nach einem Thema aus einer veröffentlichten Vorlesung.
                    """;
        }
        return """
                ## Direct answer
                I could not find relevant published material for that question.

                ## Explanation
                Try rephrasing, or ask about a topic that appears in a published lecture PDF for this course.
                """;
    }

    public String promptLine() {
        if (this == DE) {
            return """
                    Write the entire answer in German, including every heading and sentence.
                    Use these headings: ## Direkte Antwort / ## Erklärung / ## Schlüsselbegriffe.
                    The question may be in another language. Keep every fact grounded in the excerpts.""";
        }
        return """
                Write the entire answer in English, including every heading and sentence.
                Use these headings: ## Direct answer / ## Explanation / ## Key terms.
                The question may be in another language. Keep every fact grounded in the excerpts.""";
    }

    public String summaryPromptLine() {
        if (this == DE) {
            return "Write the summary in German. The lecture pages may be in another language. Translate the explanation and keep every fact grounded in those pages.";
        }
        return "Write the summary in English. The lecture pages may be in another language. Keep every fact grounded in those pages.";
    }
}

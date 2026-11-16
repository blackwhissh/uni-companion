package com.unicompanion.learning.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerPromptTest {

    @Test
    void asksForAResultTheStudentCanRepeatThenAWalkthrough() {
        String prompt = AnswerPrompt.build(
                "What do consensus algorithms do?",
                List.of(chunk("Consensus", 2, "Consensus algorithms keep replicas consistent.")),
                ExplanationLanguage.EN,
                List.of()
        );

        assertThat(prompt).contains("The first sentences are the result");
        assertThat(prompt).contains("**Result:**");
        assertThat(prompt).contains("Do not open by describing the lecture");
        assertThat(prompt).contains("from your course materials");
        assertThat(prompt).contains("## Direct answer");
        assertThat(prompt).contains("## Explanation");
        assertThat(prompt).contains("Omit the whole section when it would only repeat the result");
        assertThat(prompt).contains("[1] Consensus (page 2)");
        assertThat(prompt).contains("Question: What do consensus algorithms do?");
        assertThat(prompt).doesNotContain("Prior conversation");
    }

    @Test
    void germanPromptClosesTheWalkthroughWithErgebnis() {
        String prompt = AnswerPrompt.build(
                "Was ist Konsens?",
                List.of(chunk("Konsens", 1, "Konsens hält Replikate konsistent.")),
                ExplanationLanguage.DE,
                List.of(new ChatModel.Turn("USER", "Explain consensus"))
        );

        assertThat(prompt).contains("## Direkte Antwort");
        assertThat(prompt).contains("**Ergebnis:**");
        assertThat(prompt).contains("Prior conversation");
        assertThat(prompt).contains("Restate the result in this reply");
        assertThat(prompt).contains("Student: Explain consensus");
    }

    private static ChatModel.RetrievedChunk chunk(String title, int page, String content) {
        return new ChatModel.RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), title, page, content);
    }
}

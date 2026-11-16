package com.unicompanion.learning.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FakeChatModelTest {

    private final FakeChatModel model = new FakeChatModel();

    @Test
    void answerStatesTheLectureSentenceAsTheResult() {
        String answer = model.answer(
                "What do consensus algorithms do?",
                List.of(chunk("Lecture 1", 1, "Consensus algorithms keep replicas consistent.")),
                ExplanationLanguage.EN
        );

        assertThat(answer).contains("## Direct answer");
        assertThat(answer).contains("Consensus algorithms keep replicas consistent. [1]");
        assertThat(answer).contains("1. The lecture states: Consensus algorithms keep replicas consistent. [1]");
        assertThat(answer).contains("**Result:**");
        assertThat(answer).contains("**Consensus algorithms**");
        assertThat(answer).doesNotContain("From your course materials");
        assertThat(answer).doesNotContain("published materials support");
    }

    @Test
    void followUpKeepsTheSameResultInFewerWords() {
        String answer = model.answerWithHistory(
                "Explain that simpler.",
                List.of(chunk("Lecture 1", 1, "Consensus algorithms keep replicas consistent.")),
                ExplanationLanguage.EN,
                List.of(new ChatModel.Turn("USER", "What is consensus?"))
        );

        assertThat(answer).contains("Follow-up. Same result, in fewer words.");
        assertThat(answer).contains("**Result:**");
        assertThat(answer).contains("Consensus algorithms keep replicas consistent.");
    }

    @Test
    void germanAnswerUsesGermanHeadingsAndKeepsTheSourceSentence() {
        String answer = model.answer(
                "Was ist Konsens?",
                List.of(chunk("Konsens", 2, "Consensus algorithms keep replicas consistent.")),
                ExplanationLanguage.DE
        );

        assertThat(answer).contains("## Direkte Antwort");
        assertThat(answer).contains("**Ergebnis:**");
        assertThat(answer).contains("Consensus algorithms keep replicas consistent.");
        assertThat(answer).doesNotContain("## Direct answer");
        assertThat(answer).doesNotContain("Aus den veröffentlichten Materialien");
    }

    private static ChatModel.RetrievedChunk chunk(String title, int page, String content) {
        return new ChatModel.RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), title, page, content);
    }
}

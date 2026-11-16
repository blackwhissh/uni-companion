package com.unicompanion.learning.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight golden-question harness: fixture chunks + FakeChatModel + CitedSources.
 * Catches grounding regressions without Vertex or Testcontainers.
 */
class GoldenQaEvalTest {

    private final FakeChatModel chat = new FakeChatModel();

    @Test
    void consensusQuestionKeepsACitedDirectAnswer() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(
                chunk(
                        "Lecture 1",
                        3,
                        "Consensus algorithms keep replicas consistent by agreeing on the next value "
                                + "before committing a write."
                )
        );

        String raw = chat.answer("What do consensus algorithms do?", retrieved);
        CitedSources.Result grounded = CitedSources.select(raw, retrieved);

        assertThat(grounded.chunks()).isNotEmpty();
        assertThat(grounded.answer()).containsPattern("(?i)consensus");
        assertThat(grounded.answer()).contains("[1]");
        assertThat(CitationText.excerpt(retrieved.getFirst().content(), "What do consensus algorithms do?", grounded.answer(), 1))
                .containsIgnoringCase("replicas");
    }

    @Test
    void dualSortingRulesRecoverTwoClaimsOntoTheRuleChunk() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(
                chunk("Complexity", 8, "Quicksort average case is O(n log n) for large inputs."),
                chunk(
                        "Course rules",
                        12,
                        "In this course we always use insertion sort when n < 17. "
                                + "In the exercises we always use the median-of-three pivot rule."
                )
        );

        String raw = """
                ## Direct answer
                Insertion sort is used when n < 17 [1]. We always use the median-of-three pivot rule.

                ## Explanation
                Both rules are stated in the lecture notes.
                """;
        CitedSources.Result grounded = CitedSources.select(raw, retrieved);

        assertThat(grounded.chunks()).extracting(ChatModel.RetrievedChunk::title)
                .containsExactly("Course rules");
        assertThat(grounded.answer()).containsPattern("n < 17\\.?\\s*\\[1]");
        assertThat(grounded.answer()).containsPattern("median-of-three pivot rule\\.?\\s*\\[1]");
    }

    @Test
    void offTopicQuestionYieldsInsufficientWithoutChips() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(
                chunk("Lecture 1", 1, "Replication keeps copies of data on multiple nodes.")
        );

        String raw = chat.answer("What is the capital of France?", List.of());
        CitedSources.Result grounded = CitedSources.select(raw, retrieved);

        assertThat(grounded.chunks()).isEmpty();
        assertThat(grounded.answer()).doesNotContain("[1]");
        assertThat(grounded.answer()).containsIgnoringCase("could not find");
    }

    @Test
    void germanAvailabilityQuestionRecoversUmlautGrounding() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(
                chunk(
                        "Vorlesung 2",
                        5,
                        "Die Verfügbarkeit eines verteilten Systems beschreibt die Wahrscheinlichkeit, "
                                + "dass das System für Nutzer erreichbar und nutzbar bleibt."
                )
        );

        String raw = """
                ## Direkte Antwort
                Verfügbarkeit beschreibt die Wahrscheinlichkeit, dass das System nutzbar bleibt.

                ## Erklärung
                Der Begriff kommt in der Vorlesung vor.
                """;
        CitedSources.Result grounded = CitedSources.select(raw, retrieved);

        assertThat(grounded.chunks()).hasSize(1);
        assertThat(grounded.answer()).contains("[1]");
        assertThat(CitationText.excerpt(
                retrieved.getFirst().content(),
                "Was ist Verfügbarkeit?",
                grounded.answer(),
                1
        )).contains("Verfügbarkeit");
    }

    private static ChatModel.RetrievedChunk chunk(String title, int page, String content) {
        return new ChatModel.RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), title, page, content);
    }
}

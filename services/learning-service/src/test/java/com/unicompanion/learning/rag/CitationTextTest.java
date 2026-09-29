package com.unicompanion.learning.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CitationTextTest {

    @Test
    void extractsChapterHintAndStripsItFromExcerpt() {
        String content = """
                Distributed Systems — Study Material 3 Chapter 1 — What Is a Distributed System? \
                A distributed system is a collection of independent computers that communicate over a network.
                """;

        assertThat(CitationText.sectionHint(content))
                .isEqualTo("Chapter 1 — What Is a Distributed System?");
        assertThat(CitationText.excerpt(content))
                .startsWith("A distributed system is a collection")
                .doesNotContain("Study Material");
    }

    @Test
    void prefersConcreteDefinitionOverChecklistNoise() {
        String content = """
                Distributed Systems — Study Material 17 Chapter 15 — Final Revision Checklist \
                Foundations • Can you define a distributed system? • Can you explain partial failure? \
                A distributed system is a collection of independent computers that communicate over a network \
                and cooperate to perform a task. Partial failure means one part can fail while another continues.
                """;

        String excerpt = CitationText.excerpt(content, "What is a distributed system?", null);

        assertThat(excerpt)
                .contains("collection of independent computers")
                .doesNotContain("Can you define")
                .doesNotContain("Final Revision Checklist");
    }

    @Test
    void usesQuestionTermsToPickTheMostRelevantSentence() {
        String content = """
                Chapter 1 — Basics. Networks move packets between hosts. \
                Partial failure means one part of the system can fail while another continues running. \
                Geographic distribution places services closer to users.
                """;

        String excerpt = CitationText.excerpt(content, "What is partial failure?", "partial failure");

        assertThat(excerpt)
                .contains("Partial failure means")
                .doesNotContain("Geographic distribution");
    }

    @Test
    void reformatsFlattenedCentralizedVsDistributedTable() {
        String content = """
                Chapter 1 — What Is a Distributed System? \
                Property Centralized Distributed State Mostly local Spread across nodes \
                Communication Often local Network messages Failure scope Often concentrated \
                Partial failures possible Scaling Vertical / limited Often horizontal \
                Coordination Simpler A major design problem Consistency Usually easier \
                Must be explicitly designed
                """;

        String excerpt = CitationText.excerpt(content, "summarize the first chapter", null);

        assertThat(excerpt)
                .startsWith("Centralized vs distributed —")
                .contains("State: Mostly local → Spread across nodes")
                .contains("Communication: Often local → Network messages")
                .contains("Coordination: Simpler → A major design problem")
                .doesNotContain("Property Centralized Distributed State Mostly");
    }

    @Test
    void prefersSentenceContainingNumericClaimFromAnswer() {
        String content = """
                Chapter 4 — Sorting. Quicksort often uses a random pivot in textbooks. \
                In these exercises, Quicksort always uses median-of-three pivoting when n >= 17. \
                Insertion sort is preferred when n < 17 because the overhead of Quicksort dominates.
                """;

        String excerpt = CitationText.excerpt(
                content,
                "When is insertion sort used?",
                "Insertion sort is used when n < 17."
        );

        assertThat(excerpt)
                .contains("n < 17")
                .doesNotContain("random pivot in textbooks");
    }
}

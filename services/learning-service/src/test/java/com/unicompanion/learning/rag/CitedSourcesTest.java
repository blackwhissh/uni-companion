package com.unicompanion.learning.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CitedSourcesTest {

    @Test
    void keepsOnlyCitedChunksAndRenumbersMarkers() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(
                chunk("A", 1, "alpha"),
                chunk("B", 2, "beta"),
                chunk("C", 3, "gamma"),
                chunk("D", 4, "delta")
        );

        CitedSources.Result result = CitedSources.select(
                "Answer from [4] only. Still [4].",
                retrieved
        );

        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().getFirst().title()).isEqualTo("D");
        assertThat(result.answer()).isEqualTo("Answer from [1] only. Still [1].");
    }

    @Test
    void preservesFirstMentionOrderAcrossMultipleCitations() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(
                chunk("A", 1, "alpha"),
                chunk("B", 2, "beta"),
                chunk("C", 3, "gamma")
        );

        CitedSources.Result result = CitedSources.select("See [3] then [1].", retrieved);

        assertThat(result.chunks()).extracting(ChatModel.RetrievedChunk::title).containsExactly("C", "A");
        assertThat(result.answer()).isEqualTo("See [1] then [2].");
    }

    @Test
    void returnsNoSourcesWhenAnswerHasNoValidCitationsAndNoOverlap() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(
                chunk("A", 1, "consensus raft leader election"),
                chunk("B", 2, "vector clocks happen before")
        );

        CitedSources.Result result = CitedSources.select(
                """
                        ## Direct answer
                        Networks move packets between hosts.

                        ## Explanation
                        Details follow.
                        """,
                retrieved
        );

        assertThat(result.chunks()).isEmpty();
        assertThat(result.answer()).doesNotContain("[1]");
        assertThat(result.answer()).contains("Networks move packets between hosts.");
    }

    @Test
    void stripsPageNumberBracketsButKeepsValidExcerptCitations() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(
                chunk("A", 1, "alpha"),
                chunk("B", 2, "beta"),
                chunk("C", 3, "gamma"),
                chunk("D", 4, "delta")
        );

        CitedSources.Result result = CitedSources.select(
                "See [17] and also [2] for details.",
                retrieved
        );

        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().getFirst().title()).isEqualTo("B");
        assertThat(result.answer()).isEqualTo("See and also [1] for details.");
    }

    @Test
    void recoversOverlapWhenOnlyPageNumberBracketsWereUsed() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(
                chunk("Lecture 3", 12, "Insertion sort is always used when n < 17. Use median of three for Quicksort.")
        );

        CitedSources.Result result = CitedSources.select(
                """
                        ## Direct answer
                        Insertion sort is used when n < 17 [17].

                        ## Explanation
                        The notes mention median-of-three for Quicksort.
                        """,
                retrieved
        );

        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().getFirst().title()).isEqualTo("Lecture 3");
        assertThat(result.answer()).doesNotContain("[17]");
        assertThat(result.answer()).contains("[1]");
        assertThat(result.answer()).contains("Insertion sort is used when n < 17");
    }

    @Test
    void expandsListCitationsAndDropsOutOfRangeIndexes() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(chunk("Only", 1, "heapsort is not discussed here"));

        CitedSources.Result result = CitedSources.select(
                """
                        ## Direct answer
                        Heapsort is not covered in the published materials [1, 2, 3, 4, 6].

                        ## Explanation
                        Try another lecture PDF [2, 6].
                        """,
                retrieved
        );

        assertThat(result.chunks()).isEmpty();
        assertThat(result.answer()).doesNotContain("[");
        assertThat(result.answer()).contains("Heapsort is not covered");
    }

    @Test
    void keepsValidMembersOfListCitationsWhenAnswerIsGrounded() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(
                chunk("A", 1, "alpha fact"),
                chunk("B", 2, "beta fact")
        );

        CitedSources.Result result = CitedSources.select(
                "Consensus needs a majority [1, 2, 9].",
                retrieved
        );

        assertThat(result.chunks()).hasSize(2);
        assertThat(result.answer()).isEqualTo("Consensus needs a majority [1] [2].");
    }

    private static ChatModel.RetrievedChunk chunk(String title, int page, String content) {
        return new ChatModel.RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), title, page, content);
    }
}

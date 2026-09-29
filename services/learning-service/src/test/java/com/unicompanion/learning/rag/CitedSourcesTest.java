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
    void injectsGroundingChipWhenAnswerHasNoCitations() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(chunk("A", 1, "alpha"), chunk("B", 2, "beta"));

        CitedSources.Result result = CitedSources.select(
                """
                        ## Direct answer
                        Networks move packets between hosts.

                        ## Explanation
                        Details follow.
                        """,
                retrieved
        );

        assertThat(result.chunks()).hasSize(2);
        assertThat(result.answer()).contains("[1]");
        assertThat(result.answer()).contains("Networks move packets between hosts. [1]");
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
    void recoversWhenOnlyPageNumberBracketsWereUsed() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(chunk("Lecture 3", 12, "median of three"));

        CitedSources.Result result = CitedSources.select(
                """
                        ## Direct answer
                        Insertion sort is used when n < 17.

                        ## Explanation
                        The notes mention median-of-three for Quicksort.
                        """.replace("n < 17", "n < 17 [17]"),
                retrieved
        );

        assertThat(result.chunks()).isNotEmpty();
        assertThat(result.answer()).contains("[1]");
        assertThat(result.answer()).doesNotContain("[17]");
    }

    private static ChatModel.RetrievedChunk chunk(String title, int page, String content) {
        return new ChatModel.RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), title, page, content);
    }
}

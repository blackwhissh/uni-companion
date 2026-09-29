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
    void fallsBackToAllChunksWhenAnswerHasNoCitations() {
        List<ChatModel.RetrievedChunk> retrieved = List.of(chunk("A", 1, "alpha"), chunk("B", 2, "beta"));

        CitedSources.Result result = CitedSources.select("No markers here.", retrieved);

        assertThat(result.chunks()).hasSize(2);
        assertThat(result.answer()).isEqualTo("No markers here.");
    }

    private static ChatModel.RetrievedChunk chunk(String title, int page, String content) {
        return new ChatModel.RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), title, page, content);
    }
}

package com.unicompanion.learning.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HybridRankerTest {

    @Test
    void fusesListsWithReciprocalRankFusion() {
        UUID shared = UUID.randomUUID();
        ChatModel.RetrievedChunk a = chunk(shared, "Shared");
        ChatModel.RetrievedChunk b = chunk(UUID.randomUUID(), "VectorOnly");
        ChatModel.RetrievedChunk c = chunk(UUID.randomUUID(), "LexicalOnly");

        List<ChatModel.RetrievedChunk> fused = HybridRanker.fuse(
                3,
                List.of(a, b),
                List.of(a, c)
        );

        assertThat(fused.getFirst().title()).isEqualTo("Shared");
        assertThat(fused).extracting(ChatModel.RetrievedChunk::title)
                .containsExactlyInAnyOrder("Shared", "VectorOnly", "LexicalOnly");
    }

    @Test
    void respectsLimit() {
        ChatModel.RetrievedChunk a = chunk(UUID.randomUUID(), "A");
        ChatModel.RetrievedChunk b = chunk(UUID.randomUUID(), "B");
        ChatModel.RetrievedChunk c = chunk(UUID.randomUUID(), "C");

        List<ChatModel.RetrievedChunk> fused = HybridRanker.fuse(2, List.of(a, b, c), List.of());

        assertThat(fused).hasSize(2);
        assertThat(fused.getFirst().title()).isEqualTo("A");
    }

    private static ChatModel.RetrievedChunk chunk(UUID id, String title) {
        return new ChatModel.RetrievedChunk(id, UUID.randomUUID(), title, 1, title + " content");
    }
}

package com.unicompanion.learning.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PassageSupportTest {

    @Test
    void consensusQuestionKeepsThePageThatDefinesIt() {
        ChatModel.RetrievedChunk page = chunk(
                "Lecture 1",
                "Consensus algorithms keep replicas consistent by agreeing on the next value."
        );

        assertThat(PassageSupport.supports(
                "What do consensus algorithms do?",
                page.content()
        )).isTrue();
        assertThat(PassageSupport.keep("What do consensus algorithms do?", List.of(page))).containsExactly(page);
    }

    @Test
    void shardingQuestionDoesNotKeepAConsensusPage() {
        ChatModel.RetrievedChunk page = chunk("Lecture 1", "Consensus algorithms keep replicas consistent.");

        assertThat(PassageSupport.supports("What is sharding?", page.content())).isFalse();
        assertThat(PassageSupport.keep("What is sharding?", List.of(page))).isEmpty();
    }

    @Test
    void genericWordTopicDoesNotMakeAnUnrelatedPageAnAnswer() {
        String page = "Published topic is replication.";

        assertThat(PassageSupport.supports("What is the secret draft topic?", page)).isFalse();
        assertThat(PassageSupport.supports("What is replication?", page)).isTrue();
    }

    @Test
    void offTopicQuestionDoesNotKeepTheOnlyPage() {
        assertThat(PassageSupport.supports(
                "What is the capital of France?",
                "Consensus algorithms keep replicas consistent."
        )).isFalse();
    }

    @Test
    void numericCutoffInThePageCountsAsSupport() {
        assertThat(PassageSupport.supports(
                "Which sort is used when n < 17?",
                "In this course we always use insertion sort when n < 17."
        )).isTrue();
    }

    private static ChatModel.RetrievedChunk chunk(String title, String content) {
        return new ChatModel.RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), title, 1, content);
    }
}

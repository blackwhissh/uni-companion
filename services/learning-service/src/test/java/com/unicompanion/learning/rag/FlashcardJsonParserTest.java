package com.unicompanion.learning.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlashcardJsonParserTest {

    @Test
    void parsesPlainJsonArray() {
        List<ChatModel.FlashcardDraft> cards = FlashcardJsonParser.parse(
                """
                        [
                          {"front":"What is consensus?","back":"Agreement among replicas."},
                          {"front":"What is partial failure?","back":"One part fails while others continue."}
                        ]
                        """,
                6
        );

        assertThat(cards).hasSize(2);
        assertThat(cards.getFirst().front()).isEqualTo("What is consensus?");
        assertThat(cards.getFirst().back()).isEqualTo("Agreement among replicas.");
    }

    @Test
    void parsesFencedJsonAndRespectsLimit() {
        List<ChatModel.FlashcardDraft> cards = FlashcardJsonParser.parse(
                """
                        ```json
                        {"flashcards":[
                          {"front":"A","back":"1"},
                          {"front":"B","back":"2"},
                          {"front":"C","back":"3"}
                        ]}
                        ```
                        """,
                2
        );

        assertThat(cards).extracting(ChatModel.FlashcardDraft::front).containsExactly("A", "B");
    }

    @Test
    void returnsEmptyOnInvalidJson() {
        assertThat(FlashcardJsonParser.parse("not json", 4)).isEmpty();
    }
}

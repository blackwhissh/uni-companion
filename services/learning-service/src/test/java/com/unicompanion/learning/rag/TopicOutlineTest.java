package com.unicompanion.learning.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TopicOutlineTest {

    @Test
    void titleSlideStartsATopicAndFollowingSlidesStayInIt() {
        String threadsBody = "A thread is an activity within a process. ".repeat(12);

        List<String> topics = TopicOutline.topics(List.of(
                "Parallel Processing\nWinter Term 2026/27\nRoland Wismüller",
                "Threads and Synchronization",
                "Threads\n" + threadsBody,
                "Synchronization\nA lock protects shared memory.",
                "2. Message passing\n" + "Processes exchange messages instead of sharing memory. ".repeat(8)
        ));

        assertThat(topics).containsExactly(
                "Parallel Processing",
                "Threads and Synchronization",
                "Threads and Synchronization",
                "Threads and Synchronization",
                "2. Message passing"
        );
    }

    @Test
    void aSentenceSlideDoesNotStartATopic() {
        assertThat(TopicOutline.heading(
                "A thread is an activity within a process. Memory is shared."
        )).isNull();
    }

    @Test
    void broadLectureQuestionUsesOneNamedSection() {
        assertThat(BroadStudyQuestion.matches(
                "Walk me through the core process or algorithm step by step."
        )).isTrue();
        assertThat(BroadStudyQuestion.matches("What is a thread?")).isFalse();

        UUID material = UUID.randomUUID();
        ChatModel.RetrievedChunk cover = page(material, 1, "Parallel Processing\nWinter Term 2026/27");
        ChatModel.RetrievedChunk threadsTitle = page(material, 2, "Threads and Synchronization");
        ChatModel.RetrievedChunk threadsBody = page(
                material,
                3,
                "Threads\nA thread is an activity within a process. " + "Registers stay private. ".repeat(20)
        );
        ChatModel.RetrievedChunk syncTitle = page(material, 4, "Synchronization");
        ChatModel.RetrievedChunk syncBody = page(
                material,
                5,
                "A lock protects data that threads share. " + "Only one thread holds the lock. ".repeat(20)
        );

        List<ChatModel.RetrievedChunk> selected = TopicSelection.forBroadQuestion(
                "Walk me through synchronization step by step.",
                List.of(cover, threadsTitle, threadsBody, syncTitle, syncBody),
                List.of(threadsBody)
        );

        assertThat(selected).extracting(ChatModel.RetrievedChunk::pageNumber).containsExactly(4, 5);
        assertThat(selected.getFirst().title()).endsWith("· Synchronization");
        assertThat(selected.get(1).content()).contains("A lock protects");
    }

    @Test
    void unnamedWalkthroughUsesTheRetrievedSectionRatherThanTheCover() {
        UUID material = UUID.randomUUID();
        ChatModel.RetrievedChunk cover = page(material, 1, "Parallel Processing");
        ChatModel.RetrievedChunk threadsTitle = page(material, 2, "Threads and Synchronization");
        ChatModel.RetrievedChunk threadsBody = page(
                material,
                3,
                "A thread is an activity within a process. " + "The operating system time-multiplexes threads. ".repeat(12)
        );

        List<ChatModel.RetrievedChunk> selected = TopicSelection.forBroadQuestion(
                "Walk me through the core process step by step.",
                List.of(cover, threadsTitle, threadsBody),
                List.of(cover, threadsBody)
        );

        assertThat(selected).extracting(ChatModel.RetrievedChunk::pageNumber).containsExactly(2, 3);
        assertThat(selected.getFirst().title()).contains("Threads and Synchronization");
    }

    private static ChatModel.RetrievedChunk page(UUID materialId, int page, String content) {
        return new ChatModel.RetrievedChunk(UUID.randomUUID(), materialId, "Repetition_Foundations.pdf", page, content);
    }
}

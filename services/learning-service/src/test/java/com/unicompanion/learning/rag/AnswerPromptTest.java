package com.unicompanion.learning.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerPromptTest {

    @Test
    void broadProcessQuestionIsTaughtFromThePagesInsteadOfRefused() {
        String prompt = AnswerPrompt.build(
                "Walk me through the core process or algorithm step by step.",
                List.of(chunk(
                        "Repetition_Foundations.pdf",
                        2,
                        "A thread is an activity within a process. The OS time-multiplexes threads onto CPU cores."
                ))
        );

        assertThat(prompt).contains("You are the teacher for this lecture");
        assertThat(prompt).contains("teach the central idea of these pages");
        assertThat(prompt).contains("Do not open by saying you cannot find an algorithm");
        assertThat(prompt).contains("Omit the whole section");
        assertThat(prompt).contains("could not find");
        assertThat(prompt).contains("A thread is an activity within a process");
        assertThat(prompt).contains("Walk me through the core process");
    }

    @Test
    void followUpStillHasToComeFromThePages() {
        String prompt = AnswerPrompt.build(
                "What stays private?",
                List.of(chunk("Lecture", 4, "CPU registers and the stack pointer are private to the thread.")),
                "What is a thread?",
                "A thread is an activity within a process."
        );

        assertThat(prompt).contains("Previous question: What is a thread?");
        assertThat(prompt).contains("Still teach only from the pages below");
        assertThat(prompt).contains("CPU registers and the stack pointer are private");
    }

    private static ChatModel.RetrievedChunk chunk(String title, int page, String content) {
        return new ChatModel.RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), title, page, content);
    }
}

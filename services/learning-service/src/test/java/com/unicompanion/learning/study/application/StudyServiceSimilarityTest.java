package com.unicompanion.learning.study.application;

import com.unicompanion.learning.rag.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudyServiceSimilarityTest {

    @Test
    void detectsExactAndNearDuplicateQuestions() {
        assertThat(StudyService.similarFlashcardQuestion(
                "What is the purpose of the Raft leader?",
                "What is the purpose of a Raft leader?"
        )).isTrue();

        assertThat(StudyService.similarFlashcardQuestion(
                "How does consensus keep replicas consistent?",
                "How does consensus keep the replicas consistent?"
        )).isTrue();
    }

    @Test
    void keepsQuestionsAboutDifferentConcepts() {
        assertThat(StudyService.similarFlashcardQuestion(
                "What is consensus?",
                "How does sharding distribute data?"
        )).isFalse();
    }

    @Test
    void detectsSameFactWithDifferentQuestionWording() {
        ChatModel.FlashcardDraft first = new ChatModel.FlashcardDraft(
                "How does Raft repair conflicting log entries on a follower?",
                "When an AppendEntries RPC indicates a mismatch, the leader backs up its nextIndex for that follower and retries. Once a matching prefix is found, conflicting follower entries are deleted and replaced by the leader's entries."
        );
        ChatModel.FlashcardDraft second = new ChatModel.FlashcardDraft(
                "What is the Raft leader's response when a follower rejects an AppendEntries RPC due to a log mismatch?",
                "The leader backs up its nextIndex for that follower and retries the AppendEntries RPC."
        );

        assertThat(StudyService.similarFlashcard(first, second)).isTrue();
    }

    @Test
    void truncatesAvoidedCardSummariesForThePrompt() {
        String longBack = "The leader backs up nextIndex and retries AppendEntries until a matching prefix is found, then conflicting follower entries are deleted and replaced. ".repeat(4);
        ChatModel.FlashcardDraft card = new ChatModel.FlashcardDraft(
                "How does Raft repair conflicting log entries on a follower?",
                longBack
        );

        List<String> lines = StudyService.avoidedFlashcardSummaries(List.of(card));

        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).startsWith("Front: How does Raft repair");
        assertThat(lines.getFirst()).contains("Back:");
        assertThat(lines.getFirst().length()).isLessThan(longBack.length());
    }

    @Test
    void detectsSameQuizFactWithDifferentWording() {
        ChatModel.QuizQuestionDraft first = new ChatModel.QuizQuestionDraft(
                "How does Raft repair conflicting log entries on a follower?",
                List.of(
                        "When an AppendEntries RPC indicates a mismatch, the leader backs up its nextIndex for that follower and retries. Once a matching prefix is found, conflicting follower entries are deleted and replaced by the leader's entries.",
                        "The follower becomes leader immediately.",
                        "The leader deletes its own log.",
                        "The term number is reset to zero."
                ),
                0,
                "The leader walks nextIndex back until the logs match."
        );
        ChatModel.QuizQuestionDraft second = new ChatModel.QuizQuestionDraft(
                "What is the Raft leader's response when a follower rejects an AppendEntries RPC due to a log mismatch?",
                List.of(
                        "The leader ignores the follower until the next election.",
                        "The leader backs up its nextIndex for that follower and retries the AppendEntries RPC.",
                        "The follower's log is frozen forever.",
                        "All servers revert to followers."
                ),
                1,
                "nextIndex is decremented and the RPC is retried."
        );

        assertThat(StudyService.similarQuizQuestion(first, second)).isTrue();
    }

    @Test
    void keepsQuizQuestionsThatShareATopicButTestDifferentFacts() {
        ChatModel.QuizQuestionDraft first = new ChatModel.QuizQuestionDraft(
                "What are the three roles a server can have in Raft?",
                List.of("Followers, Candidates, or Leaders.", "Primary and replica only.", "Client and server.", "Master and worker."),
                0,
                "Raft servers are followers, candidates, or leaders."
        );
        ChatModel.QuizQuestionDraft second = new ChatModel.QuizQuestionDraft(
                "What is Election Safety in Raft?",
                List.of(
                        "Every server votes twice.",
                        "At most one leader can be elected in a term.",
                        "Logs never grow.",
                        "Terms last forever."
                ),
                1,
                "Election Safety allows at most one leader per term."
        );

        assertThat(StudyService.similarQuizQuestion(first, second)).isFalse();
    }

    @Test
    void truncatesAvoidedQuizSummariesForThePrompt() {
        String longCorrect = "The leader backs up nextIndex and retries AppendEntries until a matching prefix is found, then conflicting follower entries are deleted and replaced. ".repeat(4);
        ChatModel.QuizQuestionDraft question = new ChatModel.QuizQuestionDraft(
                "How does Raft repair conflicting log entries on a follower?",
                List.of(longCorrect, "Ignore the follower.", "Reset the term.", "Delete the leader log."),
                0,
                "Walk nextIndex back until the logs match."
        );

        List<String> lines = StudyService.avoidedQuizSummaries(List.of(question));

        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).startsWith("Prompt: How does Raft repair");
        assertThat(lines.getFirst()).contains("Correct:");
        assertThat(lines.getFirst().length()).isLessThan(longCorrect.length());
    }

    @Test
    void keepsCardsThatShareATopicButTestDifferentFacts() {
        ChatModel.FlashcardDraft first = new ChatModel.FlashcardDraft(
                "What are the three roles a server can have in Raft?",
                "Followers, Candidates, or Leaders."
        );
        ChatModel.FlashcardDraft second = new ChatModel.FlashcardDraft(
                "What is Election Safety in Raft?",
                "At most one leader can be elected in a term."
        );

        assertThat(StudyService.similarFlashcard(first, second)).isFalse();
    }
}


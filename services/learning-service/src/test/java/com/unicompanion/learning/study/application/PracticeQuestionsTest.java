package com.unicompanion.learning.study.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeQuestionsTest {

    @Test
    void keepsTheNewestQuestionForEachMissedPrompt() {
        PracticeQuestions.StoredQuestion older = new PracticeQuestions.StoredQuestion(
                "What is Raft?", "a\u001fb", 0, "old");
        PracticeQuestions.StoredQuestion newer = new PracticeQuestions.StoredQuestion(
                "What is Raft?", "c\u001fd", 1, "new");
        PracticeQuestions.StoredQuestion other = new PracticeQuestions.StoredQuestion(
                "What is a quorum?", "e\u001ff", 0, "quorum");

        List<PracticeQuestions.StoredQuestion> picked = PracticeQuestions.pick(
                List.of("What is a quorum?", "What is Raft?", "Not stored anymore"),
                List.of(newer, other, older)
        );

        assertThat(picked).containsExactly(other, newer);
    }
}

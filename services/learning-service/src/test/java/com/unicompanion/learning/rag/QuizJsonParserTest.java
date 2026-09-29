package com.unicompanion.learning.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizJsonParserTest {

    @Test
    void parsesValidQuestionsAndPreservesCorrectIndex() {
        List<ChatModel.QuizQuestionDraft> questions = QuizJsonParser.parse("""
                [
                  {
                    "prompt":"What does consensus achieve?",
                    "options":[
                      "Consistent replicas",
                      "Faster compilation",
                      "Unlimited storage",
                      "Automatic enrollment"
                    ],
                    "correctIndex":0,
                    "explanation":"Consensus keeps replicas consistent."
                  }
                ]
                """, 10);

        assertThat(questions).hasSize(1);
        assertThat(questions.getFirst().prompt()).isEqualTo("What does consensus achieve?");
        assertThat(questions.getFirst().options()).containsExactly(
                "Consistent replicas",
                "Faster compilation",
                "Unlimited storage",
                "Automatic enrollment"
        );
        assertThat(questions.getFirst().correctIndex()).isZero();
    }

    @Test
    void rejectsMalformedQuestionsInsteadOfSavingThem() {
        List<ChatModel.QuizQuestionDraft> questions = QuizJsonParser.parse("""
                [
                  {
                    "prompt":"Too few options?",
                    "options":["One","Two"],
                    "correctIndex":0,
                    "explanation":"Invalid."
                  },
                  {
                    "prompt":"Index out of range?",
                    "options":["A","B","C","D"],
                    "correctIndex":9,
                    "explanation":"Invalid."
                  }
                ]
                """, 10);

        assertThat(questions).isEmpty();
    }
}

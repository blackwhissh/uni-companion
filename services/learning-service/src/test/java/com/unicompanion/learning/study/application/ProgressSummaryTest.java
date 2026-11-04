package com.unicompanion.learning.study.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressSummaryTest {

    @Test
    void emptyHistoryHasNoScoresOrGaps() {
        ProgressSummary.Result result = ProgressSummary.summarize(List.of());

        assertThat(result.attemptCount()).isZero();
        assertThat(result.latestScore()).isNull();
        assertThat(result.bestScore()).isNull();
        assertThat(result.attempts()).isEmpty();
        assertThat(result.weakTopics()).isEmpty();
    }

    @Test
    void laterCorrectAnswerClearsThePromptAndKeepsBothAttempts() {
        Instant first = Instant.parse("2026-03-01T10:00:00Z");
        Instant second = Instant.parse("2026-03-02T10:00:00Z");
        UUID missed = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID fixed = UUID.fromString("00000000-0000-0000-0000-000000000002");

        ProgressSummary.Result result = ProgressSummary.summarize(List.of(
                attempt(missed, 0, 1, first, item("What is Raft?", false, 0)),
                attempt(fixed, 1, 1, second, item("What is Raft?", true, 0))
        ));

        assertThat(result.attemptCount()).isEqualTo(2);
        assertThat(result.latestScore()).isEqualTo(1);
        assertThat(result.latestTotal()).isEqualTo(1);
        assertThat(result.bestScore()).isEqualTo(1);
        assertThat(result.attempts()).extracting(ProgressSummary.AttemptScore::id).containsExactly(fixed, missed);
        assertThat(result.weakTopics()).isEmpty();
    }

    @Test
    void repeatedMissesStayWeakAndAHigherRatioIsTheBestScore() {
        Instant early = Instant.parse("2026-03-01T10:00:00Z");
        Instant later = Instant.parse("2026-03-03T10:00:00Z");

        ProgressSummary.Result result = ProgressSummary.summarize(List.of(
                attempt(UUID.randomUUID(), 1, 2, early, item("What is Raft?", false, 0), item("  ", false, 1)),
                attempt(UUID.randomUUID(), 1, 1, later, item("What is Raft?", false, 0))
        ));

        assertThat(result.bestScore()).isEqualTo(1);
        assertThat(result.bestTotal()).isEqualTo(1);
        assertThat(result.weakTopics()).hasSize(1);
        assertThat(result.weakTopics().getFirst().prompt()).isEqualTo("What is Raft?");
        assertThat(result.weakTopics().getFirst().missCount()).isEqualTo(2);
        assertThat(result.weakTopics().getFirst().lastMissedAt()).isEqualTo(later);
    }

    private static ProgressSummary.Attempt attempt(
            UUID id,
            int score,
            int total,
            Instant createdAt,
            ProgressSummary.Item... items
    ) {
        return new ProgressSummary.Attempt(id, score, total, createdAt, List.of(items));
    }

    private static ProgressSummary.Item item(String prompt, boolean correct, int sortOrder) {
        return new ProgressSummary.Item(prompt, correct, sortOrder);
    }
}

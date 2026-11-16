package com.unicompanion.learning.study.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardGapsTest {

    @Test
    void appendsAgainCardsWithoutDuplicatingAQuizGap() {
        Instant missed = Instant.parse("2026-11-01T10:00:00Z");
        Instant marked = Instant.parse("2026-11-16T10:00:00Z");
        List<ProgressSummary.WeakTopic> merged = CardGaps.merge(
                List.of(new ProgressSummary.WeakTopic("What is Raft?", 2, missed)),
                List.of("What is Raft?", "Name the leader election timeout", "  "),
                marked
        );

        assertThat(merged).extracting(ProgressSummary.WeakTopic::prompt)
                .containsExactly("What is Raft?", "Name the leader election timeout");
        assertThat(merged.get(1).lastMissedAt()).isEqualTo(marked);
    }
}

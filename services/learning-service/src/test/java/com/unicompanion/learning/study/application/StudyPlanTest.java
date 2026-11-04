package com.unicompanion.learning.study.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudyPlanTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    @Test
    void noQuizzesYetSchedulesAQuizTodayAndAReviewOnTheExam() {
        StudyPlan.Result plan = StudyPlan.build(TODAY, TODAY.plusDays(2), ProgressSummary.summarize(List.of()));

        assertThat(plan.daysUntilExam()).isEqualTo(2);
        assertThat(plan.steps()).containsExactly(
                new StudyPlan.Step(TODAY, StudyPlan.Kind.QUIZ, null, 0),
                new StudyPlan.Step(TODAY.plusDays(2), StudyPlan.Kind.REVIEW, null, 0)
        );
    }

    @Test
    void missedPromptsAreSpreadBeforeTheExamAndAReviewClosesThePlan() {
        ProgressSummary.Result progress = ProgressSummary.summarize(List.of(
                attempt(false, "What is Raft?"),
                attempt(false, "What is a quorum?")
        ));

        StudyPlan.Result plan = StudyPlan.build(TODAY, TODAY.plusDays(2), progress);

        assertThat(plan.steps()).containsExactly(
                new StudyPlan.Step(TODAY, StudyPlan.Kind.GAP, "What is Raft?", 1),
                new StudyPlan.Step(TODAY.plusDays(1), StudyPlan.Kind.GAP, "What is a quorum?", 1),
                new StudyPlan.Step(TODAY.plusDays(2), StudyPlan.Kind.REVIEW, null, 0)
        );
    }

    @Test
    void anExamTodayKeepsTheGapAndTheReviewOnTheSameDay() {
        ProgressSummary.Result progress = ProgressSummary.summarize(List.of(attempt(false, "What is Raft?")));

        StudyPlan.Result plan = StudyPlan.build(TODAY, TODAY, progress);

        assertThat(plan.daysUntilExam()).isZero();
        assertThat(plan.steps()).containsExactly(
                new StudyPlan.Step(TODAY, StudyPlan.Kind.GAP, "What is Raft?", 1),
                new StudyPlan.Step(TODAY, StudyPlan.Kind.REVIEW, null, 0)
        );
    }

    @Test
    void aClearedGapLeavesOnlyTheExamReview() {
        Instant first = Instant.parse("2026-11-01T10:00:00Z");
        Instant second = Instant.parse("2026-11-02T10:00:00Z");
        ProgressSummary.Result progress = ProgressSummary.summarize(List.of(
                new ProgressSummary.Attempt(UUID.randomUUID(), 0, 1, first, List.of(new ProgressSummary.Item("What is Raft?", false, 0))),
                new ProgressSummary.Attempt(UUID.randomUUID(), 1, 1, second, List.of(new ProgressSummary.Item("What is Raft?", true, 0)))
        ));

        StudyPlan.Result plan = StudyPlan.build(TODAY, TODAY.plusDays(1), progress);

        assertThat(plan.steps()).containsExactly(
                new StudyPlan.Step(TODAY.plusDays(1), StudyPlan.Kind.REVIEW, null, 0)
        );
    }

    @Test
    void aPastExamIsNotANewSchedule() {
        StudyPlan.Result plan = StudyPlan.build(TODAY, TODAY.minusDays(1), ProgressSummary.summarize(List.of()));

        assertThat(plan.daysUntilExam()).isEqualTo(-1);
        assertThat(plan.steps()).containsExactly(
                new StudyPlan.Step(TODAY, StudyPlan.Kind.EXAM_PASSED, null, 0)
        );
    }

    @Test
    void anExamMoreThanAYearAwayIsRejected() {
        assertThatThrownBy(() -> StudyPlan.build(TODAY, TODAY.plusDays(367), ProgressSummary.summarize(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Choose an exam date within a year.");
    }

    private static ProgressSummary.Attempt attempt(boolean correct, String prompt) {
        return new ProgressSummary.Attempt(
                UUID.randomUUID(),
                correct ? 1 : 0,
                1,
                Instant.parse("2026-11-03T10:00:00Z"),
                List.of(new ProgressSummary.Item(prompt, correct, 0))
        );
    }
}

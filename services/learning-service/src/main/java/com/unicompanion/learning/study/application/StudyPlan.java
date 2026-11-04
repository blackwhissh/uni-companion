package com.unicompanion.learning.study.application;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * A dated list of quiz gaps before an exam. Steps are derived from progress
 * each time the plan is read, so a later correct answer drops that gap.
 */
public final class StudyPlan {

    public static final int MAX_HORIZON_DAYS = 366;

    private StudyPlan() {
    }

    public static Result build(LocalDate today, LocalDate examDate, ProgressSummary.Result progress) {
        if (today == null || examDate == null || progress == null) {
            throw new IllegalArgumentException("Choose an exam date.");
        }
        int daysUntil = (int) ChronoUnit.DAYS.between(today, examDate);
        if (daysUntil < 0) {
            return new Result(examDate, daysUntil, List.of(new Step(today, Kind.EXAM_PASSED, null, 0)));
        }
        if (daysUntil > MAX_HORIZON_DAYS) {
            throw new IllegalArgumentException("Choose an exam date within a year.");
        }

        List<Step> steps = new ArrayList<>();
        if (progress.attemptCount() == 0) {
            steps.add(new Step(today, Kind.QUIZ, null, 0));
            steps.add(new Step(examDate, Kind.REVIEW, null, 0));
            return new Result(examDate, daysUntil, List.copyOf(steps));
        }
        List<ProgressSummary.WeakTopic> gaps = progress.weakTopics();
        if (gaps.isEmpty()) {
            steps.add(new Step(examDate, Kind.REVIEW, null, 0));
            return new Result(examDate, daysUntil, List.copyOf(steps));
        }
        List<LocalDate> workDays = workDays(today, examDate);
        for (int i = 0; i < gaps.size(); i++) {
            ProgressSummary.WeakTopic gap = gaps.get(i);
            LocalDate day = workDays.get(i * workDays.size() / gaps.size());
            steps.add(new Step(day, Kind.GAP, gap.prompt(), gap.missCount()));
        }
        steps.add(new Step(examDate, Kind.REVIEW, null, 0));
        return new Result(examDate, daysUntil, List.copyOf(steps));
    }

    private static List<LocalDate> workDays(LocalDate today, LocalDate examDate) {
        if (today.equals(examDate)) {
            return List.of(today);
        }
        List<LocalDate> days = new ArrayList<>();
        LocalDate day = today;
        while (day.isBefore(examDate)) {
            days.add(day);
            day = day.plusDays(1);
        }
        return days;
    }

    public enum Kind {
        QUIZ,
        GAP,
        REVIEW,
        EXAM_PASSED
    }

    public record Step(LocalDate date, Kind kind, String prompt, int missCount) {
    }

    public record Result(LocalDate examDate, int daysUntilExam, List<Step> steps) {
    }
}

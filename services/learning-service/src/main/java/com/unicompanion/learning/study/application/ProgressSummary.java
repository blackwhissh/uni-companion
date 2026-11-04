package com.unicompanion.learning.study.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Turns one student's quiz attempts into scores and still-missed prompts.
 * A prompt leaves the gap list when that student's latest answer is correct.
 */
public final class ProgressSummary {

    public static final int MAX_ATTEMPTS = 10;
    public static final int MAX_WEAK_TOPICS = 8;

    private ProgressSummary() {
    }

    public static Result summarize(List<Attempt> attempts) {
        List<Attempt> chronological = attempts.stream()
                .sorted(Comparator.comparing(Attempt::createdAt).thenComparing(Attempt::id))
                .toList();

        LinkedHashMap<String, TopicState> topics = new LinkedHashMap<>();
        for (Attempt attempt : chronological) {
            List<Item> items = attempt.items().stream()
                    .sorted(Comparator.comparingInt(Item::sortOrder))
                    .toList();
            for (Item item : items) {
                String prompt = item.prompt() == null ? "" : item.prompt().trim();
                if (prompt.isEmpty()) {
                    continue;
                }
                TopicState previous = topics.get(prompt);
                int misses = previous == null ? 0 : previous.missCount();
                Instant lastMissedAt = previous == null ? null : previous.lastMissedAt();
                if (!item.correct()) {
                    misses++;
                    lastMissedAt = attempt.createdAt();
                }
                topics.put(prompt, new TopicState(misses, lastMissedAt, item.correct()));
            }
        }

        List<WeakTopic> weak = new ArrayList<>();
        for (var entry : topics.entrySet()) {
            TopicState state = entry.getValue();
            if (!state.latestCorrect() && state.missCount() > 0) {
                weak.add(new WeakTopic(entry.getKey(), state.missCount(), state.lastMissedAt()));
            }
        }
        weak.sort(Comparator
                .comparing(WeakTopic::lastMissedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparingInt(WeakTopic::missCount).reversed())
                .thenComparing(WeakTopic::prompt));
        if (weak.size() > MAX_WEAK_TOPICS) {
            weak = new ArrayList<>(weak.subList(0, MAX_WEAK_TOPICS));
        }

        List<Attempt> newestFirst = chronological.stream()
                .sorted(Comparator.comparing(Attempt::createdAt).reversed().thenComparing(Attempt::id))
                .toList();
        Attempt latest = newestFirst.isEmpty() ? null : newestFirst.getFirst();
        Attempt best = chronological.stream()
                .max(Comparator
                        .comparingDouble(ProgressSummary::ratio)
                        .thenComparingInt(Attempt::score)
                        .thenComparing(Attempt::createdAt))
                .orElse(null);
        List<AttemptScore> shown = newestFirst.stream()
                .limit(MAX_ATTEMPTS)
                .map(attempt -> new AttemptScore(attempt.id(), attempt.score(), attempt.total(), attempt.createdAt()))
                .toList();

        return new Result(
                chronological.size(),
                latest == null ? null : latest.score(),
                latest == null ? null : latest.total(),
                best == null ? null : best.score(),
                best == null ? null : best.total(),
                shown,
                List.copyOf(weak)
        );
    }

    private static double ratio(Attempt attempt) {
        if (attempt.total() <= 0) {
            return 0;
        }
        return (double) attempt.score() / attempt.total();
    }

    private record TopicState(int missCount, Instant lastMissedAt, boolean latestCorrect) {
    }

    public record Attempt(UUID id, int score, int total, Instant createdAt, List<Item> items) {
    }

    public record Item(String prompt, boolean correct, int sortOrder) {
    }

    public record AttemptScore(UUID id, int score, int total, Instant createdAt) {
    }

    public record WeakTopic(String prompt, int missCount, Instant lastMissedAt) {
    }

    public record Result(
            int attemptCount,
            Integer latestScore,
            Integer latestTotal,
            Integer bestScore,
            Integer bestTotal,
            List<AttemptScore> attempts,
            List<WeakTopic> weakTopics
    ) {
    }
}

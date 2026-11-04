package com.unicompanion.learning.matching.application;

import com.unicompanion.learning.matching.domain.CollaborationIntent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Suggests other opted-in classmates who share at least one collaboration intent.
 * Email is never part of a suggestion.
 */
public final class PartnerSuggestions {

    private PartnerSuggestions() {
    }

    public static List<Match> match(Set<CollaborationIntent> mine, List<Candidate> others) {
        if (mine == null || mine.isEmpty() || others == null || others.isEmpty()) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        for (Candidate other : others) {
            if (other.displayName() == null || other.displayName().isBlank()) {
                continue;
            }
            List<CollaborationIntent> shared = shared(mine, other.intents());
            if (shared.isEmpty()) {
                continue;
            }
            matches.add(new Match(other.userId(), other.displayName().trim(), shared));
        }
        matches.sort(Comparator
                .comparing((Match item) -> item.displayName().toLowerCase())
                .thenComparing(Match::userId));
        return List.copyOf(matches);
    }

    private static List<CollaborationIntent> shared(Set<CollaborationIntent> mine, Set<CollaborationIntent> theirs) {
        if (theirs == null || theirs.isEmpty()) {
            return List.of();
        }
        List<CollaborationIntent> shared = new ArrayList<>();
        for (CollaborationIntent intent : CollaborationIntent.values()) {
            if (mine.contains(intent) && theirs.contains(intent)) {
                shared.add(intent);
            }
        }
        return shared;
    }

    public static Set<CollaborationIntent> parse(List<String> raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Choose collaboration intents.");
        }
        Set<CollaborationIntent> intents = EnumSet.noneOf(CollaborationIntent.class);
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Choose a known collaboration intent.");
            }
            try {
                intents.add(CollaborationIntent.valueOf(value.trim()));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown collaboration intent.");
            }
        }
        return intents;
    }

    public record Candidate(UUID userId, String displayName, Set<CollaborationIntent> intents) {
    }

    public record Match(UUID userId, String displayName, List<CollaborationIntent> sharedIntents) {
    }
}

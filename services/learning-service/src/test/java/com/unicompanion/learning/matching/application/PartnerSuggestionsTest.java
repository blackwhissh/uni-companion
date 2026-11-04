package com.unicompanion.learning.matching.application;

import com.unicompanion.learning.matching.domain.CollaborationIntent;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartnerSuggestionsTest {

    private static final UUID ADA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GRACE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void emptyIntentProducesNoSuggestions() {
        List<PartnerSuggestions.Match> matches = PartnerSuggestions.match(
                Set.of(),
                List.of(candidate(ADA, "Ada", CollaborationIntent.STUDY_GROUP))
        );

        assertThat(matches).isEmpty();
    }

    @Test
    void onlyClassmatesWithASharedIntentAreListed() {
        List<PartnerSuggestions.Match> matches = PartnerSuggestions.match(
                EnumSet.of(CollaborationIntent.STUDY_GROUP, CollaborationIntent.THESIS),
                List.of(
                        candidate(GRACE, "Grace", CollaborationIntent.PROJECT),
                        candidate(ADA, "  Ada  ", CollaborationIntent.THESIS, CollaborationIntent.LANGUAGE)
                )
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().userId()).isEqualTo(ADA);
        assertThat(matches.getFirst().displayName()).isEqualTo("Ada");
        assertThat(matches.getFirst().sharedIntents()).containsExactly(CollaborationIntent.THESIS);
    }

    @Test
    void blankNamesAreNotSuggested() {
        List<PartnerSuggestions.Match> matches = PartnerSuggestions.match(
                Set.of(CollaborationIntent.PROJECT),
                List.of(candidate(ADA, " ", CollaborationIntent.PROJECT))
        );

        assertThat(matches).isEmpty();
    }

    @Test
    void unknownIntentIsRejected() {
        assertThatThrownBy(() -> PartnerSuggestions.parse(List.of("STUDY_GROUP", "COFFEE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown collaboration intent.");
    }

    private static PartnerSuggestions.Candidate candidate(UUID id, String name, CollaborationIntent... intents) {
        return new PartnerSuggestions.Candidate(id, name, EnumSet.copyOf(List.of(intents)));
    }
}

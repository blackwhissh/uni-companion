package com.unicompanion.learning.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QaTurnJsonTest {

    @Test
    void roundTripsCitationsWithEscapes() {
        UUID materialId = UUID.randomUUID();
        List<RagService.Citation> citations = List.of(
                new RagService.Citation(
                        materialId,
                        "Lecture \"1\"",
                        "Chapter 1 — Basics",
                        3,
                        "Line one\nLine two"
                )
        );

        String json = QaTurnJson.citations(citations);
        List<RagService.Citation> parsed = QaTurnJson.parseCitations(json);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().materialId()).isEqualTo(materialId);
        assertThat(parsed.getFirst().title()).isEqualTo("Lecture \"1\"");
        assertThat(parsed.getFirst().sectionHint()).isEqualTo("Chapter 1 — Basics");
        assertThat(parsed.getFirst().pageNumber()).isEqualTo(3);
        assertThat(parsed.getFirst().excerpt()).isEqualTo("Line one\nLine two");
    }
}

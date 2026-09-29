package com.unicompanion.learning.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic stand-in so CI can assert grounded answers without Vertex chat.
 * When {@code unicompanion.gemini.enabled=true}, {@link VertexChatModel} is {@code @Primary}.
 */
@Component
public class FakeChatModel implements ChatModel {

    @Override
    public String answer(String question, List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return """
                    ## Direct answer
                    I could not find relevant published material for that question.

                    ## Explanation
                    Try rephrasing, or ask about a topic that appears in a published lecture PDF for this course.
                    """;
        }
        RetrievedChunk primary = chunks.getFirst();
        String body = truncate(primary.content(), 320);
        StringBuilder terms = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            String snippet = truncate(chunk.content(), 80);
            terms.append("- **From %s (p.%d)** — %s [%d]%n".formatted(
                    chunk.title(),
                    chunk.pageNumber(),
                    snippet,
                    i + 1
            ));
        }
        return """
                ## Direct answer
                From your course materials: %s [%d]

                ## Explanation
                The published materials support this answer. Source [%d] (%s, page %d) is the closest match to your question: "%s".

                ## Key terms
                %s
                """.formatted(
                body,
                1,
                1,
                primary.title(),
                primary.pageNumber(),
                question == null ? "" : question.trim(),
                terms.toString().trim()
        );
    }

    @Override
    public List<FlashcardDraft> generateFlashcards(
            List<RetrievedChunk> chunks,
            int count,
            List<String> questionsToAvoid
    ) {
        List<FlashcardDraft> cards = new ArrayList<>();
        if (chunks.isEmpty() || count <= 0) {
            return cards;
        }
        int baseVariant = fakeVariant(questionsToAvoid);
        // Produce up to `count` cards even when few chunks exist (CI fixtures often have 1–2).
        // Each card must be lexically distinct so StudyService novelty filtering keeps a full deck.
        for (int i = 0; i < count; i++) {
            RetrievedChunk chunk = chunks.get(i % chunks.size());
            cards.add(new FlashcardDraft(
                    fakeFlashcardFront(chunk, baseVariant, i),
                    fakeFlashcardBack(chunk, baseVariant, i)
            ));
        }
        return cards;
    }

    private static String fakeFlashcardFront(RetrievedChunk chunk, int baseVariant, int index) {
        int angle = (baseVariant * 7 + index) % 8;
        return switch (angle) {
            case 1 -> "Define the core claim on page %d of %s using your own wording (slot %d)."
                    .formatted(chunk.pageNumber(), chunk.title(), index + 1);
            case 2 -> "Name one failure mode a student should watch for after reading %s p.%d (probe %d)."
                    .formatted(chunk.title(), chunk.pageNumber(), index + 1);
            case 3 -> "How would you teach the idea from %s page %d to a classmate (variant %d)?"
                    .formatted(chunk.title(), chunk.pageNumber(), index + 1);
            case 4 -> "What exam trap is suggested by %s on page %d (item %d)?"
                    .formatted(chunk.title(), chunk.pageNumber(), index + 1);
            case 5 -> "List the prerequisites implied by %s page %d before applying it (card %d)."
                    .formatted(chunk.title(), chunk.pageNumber(), index + 1);
            case 6 -> "Contrast the page-%d takeaway in %s with a common misconception (focus %d)."
                    .formatted(chunk.pageNumber(), chunk.title(), index + 1);
            case 7 -> "Which concrete example best anchors %s page %d for recall practice (seed %d)?"
                    .formatted(chunk.title(), chunk.pageNumber(), index + 1);
            default -> "Summarize the actionable step from %s page %d for active recall (index %d)."
                    .formatted(chunk.title(), chunk.pageNumber(), index + 1);
        };
    }

    private static String fakeFlashcardBack(RetrievedChunk chunk, int baseVariant, int index) {
        int angle = (baseVariant * 7 + index) % 8;
        String snippet = truncate(chunk.content(), 120);
        return switch (angle) {
            case 1 -> "Restate the claim without copying: %s — then check against the PDF (slot %d)."
                    .formatted(snippet, index + 1);
            case 2 -> "Watch for skipping edge cases when applying this lecture idea; page evidence: %s (probe %d)."
                    .formatted(snippet, index + 1);
            case 3 -> "Teach it as a short story: setup, mechanism, outcome. Source: %s (variant %d)."
                    .formatted(snippet, index + 1);
            case 4 -> "Exam trap: memorizing wording instead of deciding when the rule applies. Hint: %s (item %d)."
                    .formatted(snippet, index + 1);
            case 5 -> "Prerequisites usually include prior definitions on earlier pages; related text: %s (card %d)."
                    .formatted(snippet, index + 1);
            case 6 -> "Misconception: the method always wins. Reality depends on constraints described here: %s (focus %d)."
                    .formatted(snippet, index + 1);
            case 7 -> "Anchor with a tiny scenario you invent, then verify against: %s (seed %d)."
                    .formatted(snippet, index + 1);
            default -> "Actionable step: close the PDF and reconstruct this idea from memory — %s (index %d)."
                    .formatted(snippet, index + 1);
        };
    }

    @Override
    public List<QuizQuestionDraft> generateQuiz(
            List<RetrievedChunk> chunks,
            int count,
            List<String> questionsToAvoid
    ) {
        List<QuizQuestionDraft> questions = new ArrayList<>();
        int variant = fakeVariant(questionsToAvoid);
        for (RetrievedChunk chunk : chunks) {
            if (questions.size() >= count) {
                break;
            }
            questions.add(new QuizQuestionDraft(
                    fakeQuizPrompt(chunk, variant),
                    List.of(
                            fakeQuizCorrect(chunk, variant),
                            "Students should ignore official lecture materials.",
                            "Enrollment is optional for studying published PDFs.",
                            "Unpublished drafts are always included in retrieval."
                    ),
                    0,
                    fakeQuizExplanation(chunk, variant)
            ));
        }
        return questions;
    }

    private static String fakeQuizPrompt(RetrievedChunk chunk, int variant) {
        return switch (variant % 3) {
            case 1 -> "Which study outcome should a student take from %s, page %d?"
                    .formatted(chunk.title(), chunk.pageNumber());
            case 2 -> "Why is page %d of %s useful before an exam?"
                    .formatted(chunk.pageNumber(), chunk.title());
            default -> "According to %s (page %d), which statement is supported?"
                    .formatted(chunk.title(), chunk.pageNumber());
        };
    }

    private static String fakeQuizCorrect(RetrievedChunk chunk, int variant) {
        return switch (variant % 3) {
            case 1 -> "Apply the lecture idea to a new scenario rather than reciting the same sentence.";
            case 2 -> "It helps when you still cannot reconstruct the argument from memory.";
            default -> truncate(chunk.content(), 120);
        };
    }

    private static String fakeQuizExplanation(RetrievedChunk chunk, int variant) {
        return switch (variant % 3) {
            case 1 -> "This page is for applying the concept, not copying the original wording.";
            case 2 -> "Rereading is useful only after an unsuccessful attempt to recall the argument.";
            default -> "From %s (page %d): %s".formatted(
                    chunk.title(),
                    chunk.pageNumber(),
                    truncate(chunk.content(), 220)
            );
        };
    }

    private static int fakeVariant(List<String> questionsToAvoid) {
        if (questionsToAvoid == null || questionsToAvoid.isEmpty()) {
            return 0;
        }
        return 1 + (questionsToAvoid.size() % 2);
    }

    private static String truncate(String value, int max) {
        String text = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 3) + "...";
    }
}

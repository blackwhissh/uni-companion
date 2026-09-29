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
        for (int i = 0; i < count; i++) {
            RetrievedChunk chunk = chunks.get(i % chunks.size());
            int variant = baseVariant + i;
            cards.add(new FlashcardDraft(
                    fakeFlashcardFront(chunk, variant, i),
                    fakeFlashcardBack(chunk, variant, i)
            ));
        }
        return cards;
    }

    private static String fakeFlashcardFront(RetrievedChunk chunk, int variant, int index) {
        String focus = switch (variant % 3) {
            case 1 -> "Which exam skill does %s, page %d, help you practice (#%d)?"
                    .formatted(chunk.title(), chunk.pageNumber(), index + 1);
            case 2 -> "Why might a student reread %s on page %d before an assessment (#%d)?"
                    .formatted(chunk.title(), chunk.pageNumber(), index + 1);
            default -> "What is stated in %s on page %d (card %d)?"
                    .formatted(chunk.title(), chunk.pageNumber(), index + 1);
        };
        return focus;
    }

    private static String fakeFlashcardBack(RetrievedChunk chunk, int variant, int index) {
        return switch (variant % 3) {
            case 1 -> "Use this page to practice applying the lecture idea to a new scenario (#%d)."
                    .formatted(index + 1);
            case 2 -> "Rereading helps when you still cannot reconstruct the argument from memory (#%d)."
                    .formatted(index + 1);
            default -> truncate(chunk.content(), 240) + " (angle " + (index + 1) + ")";
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

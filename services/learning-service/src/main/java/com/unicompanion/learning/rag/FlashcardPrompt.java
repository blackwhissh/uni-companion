package com.unicompanion.learning.rag;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class FlashcardPrompt {

    private FlashcardPrompt() {
    }

    static String build(List<ChatModel.RetrievedChunk> chunks, int count) {
        return build(chunks, count, List.of());
    }

    static String build(List<ChatModel.RetrievedChunk> chunks, int count, List<String> questionsToAvoid) {
        String context = IntStream.range(0, chunks.size())
                .mapToObj(i -> {
                    ChatModel.RetrievedChunk chunk = chunks.get(i);
                    return "[%d] %s (page %d)%n%s".formatted(
                            i + 1,
                            chunk.title(),
                            chunk.pageNumber(),
                            chunk.content()
                    );
                })
                .collect(Collectors.joining("\n\n"));
        String avoidedQuestions = questionsToAvoid == null || questionsToAvoid.isEmpty()
                ? "None."
                : questionsToAvoid.stream()
                        .map(card -> "- " + card)
                        .collect(Collectors.joining("\n"));

        return """
                Goal
                Convert the most important and exam-relevant information from the material into clear, useful flashcards that help with active recall.

                Instructions
                Use only the provided material
                - Do not invent information.
                - Do not add unrelated background knowledge.
                - If something is unclear or ambiguous in the material, do not guess.

                Focus on important information
                Prioritize:
                - Key concepts and definitions
                - Important terminology
                - Principles and rules
                - Processes and mechanisms
                - Important comparisons and differences
                - Formulas and their meanings
                - Examples that clarify important concepts
                - Cause-and-effect relationships
                - Important facts likely to be tested

                Keep each flashcard focused
                - One question/concept per card.
                - Do not combine several unrelated concepts into one card.
                - Avoid unnecessarily detailed questions.
                - Avoid trivial information unless it is important for understanding the topic.

                Write clear fronts
                - Use direct questions or concise prompts.
                - The question should make it obvious what information is being tested.
                - Avoid vague questions such as "Explain this topic."
                - Prefer questions such as:
                  "What is X?"
                  "What is the purpose of X?"
                  "How does X work?"
                  "What is the difference between X and Y?"
                  "What are the main steps of X?"
                  "Why is X important?"

                Write concise backs
                - Give the exact information needed to answer the question.
                - Use simple, professional language.
                - Remove unnecessary explanations and filler.
                - Include a short example only when it materially improves understanding.
                - Do not simply copy large passages from the source.

                Preserve important precision
                - Do not oversimplify technical concepts to the point of becoming inaccurate.
                - Preserve important conditions, exceptions, terminology, numbers, and relationships.
                - For formulas, include the formula and briefly explain the variables when necessary.

                Avoid redundancy
                - Do not create multiple cards testing essentially the same fact.
                - Prefer the clearest formulation when several possible cards cover the same information.
                - Do not repeat or closely paraphrase any card in the "Cards already used" list.
                - Do not test the same fact with a differently worded question or a nearly identical answer.

                Variety when prior decks exist
                - If "Cards already used" is not empty, build a distinctly different deck.
                - Prefer uncovered topics, alternate angles, worked examples, comparisons, edge cases, and applications.
                - Avoid starting with the same fundamentals already covered by earlier decks.

                Organize logically
                - Follow the structure of the source material where practical.
                - Group cards by topic or section.
                - Make the progression from fundamental concepts to more advanced concepts natural.

                Quality standard
                Every flashcard must be: accurate, relevant, concise, self-contained, easy to understand, useful for active recall, based only on the provided material, and free of unnecessary wording.

                Final check before answering
                - Remove redundant cards.
                - Remove trivial information.
                - Check that every answer actually answers its question.
                - Check technical accuracy against the source.
                - Make sure each card tests one clear idea.
                - Make the cards concise enough to review quickly.

                Output format
                Create up to %d flashcards covering the important concepts in the material.
                Prefer fewer high-quality cards over padding to the maximum.
                Do not provide a summary before or after the flashcards.
                Do not include citation markers like [1] in front or back.

                Return ONLY a JSON array (no markdown fences, no commentary) with this shape:
                [
                  {"front":"What is ...?","back":"..."},
                  {"front":"Why does ...?","back":"..."}
                ]

                Cards already used in other shared decks:
                %s

                Material excerpts:
                %s
                """.formatted(count, avoidedQuestions, context);
    }
}

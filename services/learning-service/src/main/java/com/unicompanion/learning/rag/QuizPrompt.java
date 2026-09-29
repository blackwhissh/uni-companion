package com.unicompanion.learning.rag;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class QuizPrompt {

    private QuizPrompt() {
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
                        .map(question -> "- " + question)
                        .collect(Collectors.joining("\n"));

        return """
                Create an exam-relevant multiple-choice quiz using only the supplied course material.

                Requirements:
                - Produce up to %d high-quality questions; prefer fewer questions over trivial filler.
                - Test important definitions, principles, mechanisms, comparisons, formulas, conditions,
                  cause-and-effect relationships, and applications.
                - Each question must test one clear idea and be answerable from the material alone.
                - Use exactly four concise options per question with exactly one correct answer.
                - Distractors must be plausible and based on likely misunderstandings; never use generic,
                  obviously false, humorous, or unrelated options.
                - Vary the correct option position across the quiz.
                - Preserve technical wording, conditions, numbers, formulas, and exceptions.
                - Do not invent facts or use outside knowledge.
                - Avoid duplicate questions and avoid merely asking which statement appears in a source.
                - Do not repeat or closely paraphrase any item in the "Questions already used" list.
                - Do not test the same fact with a differently worded stem or a nearly identical correct answer.
                - Explanations must briefly state why the answer is correct based on the material.
                - Do not include source markers such as [1] in questions or options.

                Before responding, remove redundant or trivial questions and verify every correct answer.

                Return ONLY a JSON array with this shape:
                [
                  {
                    "prompt":"Question?",
                    "options":["Option A","Option B","Option C","Option D"],
                    "correctIndex":0,
                    "explanation":"Concise source-grounded explanation."
                  }
                ]

                Questions already used in other shared quizzes:
                %s

                Material excerpts:
                %s
                """.formatted(count, avoidedQuestions, context);
    }
}

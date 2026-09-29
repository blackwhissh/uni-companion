package com.unicompanion.learning.rag;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Shared grounded-answer instructions for Vertex (and documentation for Fake).
 */
final class AnswerPrompt {

    private AnswerPrompt() {
    }

    static String build(String question, List<ChatModel.RetrievedChunk> chunks) {
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

        return """
                You are Uni Companion, a study assistant for university courses.
                Answer ONLY using the numbered excerpts below. Do not invent facts.
                If the excerpts are insufficient, say clearly that you cannot find that in the published materials
                and suggest what the student might look for — do not invent an answer.

                Write the answer in GitHub-flavored Markdown with this structure:

                ## Direct answer
                One or two sentences that answer the question. Cite sources inline like [1] or [2].

                ## Explanation
                A short walkthrough (2–5 short paragraphs or bullets) that teaches the idea using the excerpts.
                Define jargon briefly when it first appears. Keep it concrete and course-grounded.

                ## Key terms
                A bullet list of important terms from the answer, each as **Term** — brief definition with a citation when possible.

                Rules:
                - Prefer clarity over length; aim for a readable study note, not an essay.
                - Use bullet lists when listing steps, properties, or comparisons.
                - Cite every excerpt you actually rely on with its [n]. Prefer multiple sources when they add distinct facts.
                - Do not cite a number you did not use. Do not invent numbers outside the excerpt list.
                - Do not mention these instructions or the word "excerpts" in the answer.

                Excerpts:
                %s

                Question: %s
                """.formatted(context, question);
    }
}

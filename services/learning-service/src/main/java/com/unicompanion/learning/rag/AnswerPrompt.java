package com.unicompanion.learning.rag;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Shared grounded-answer instructions for Vertex (and documentation for Fake).
 * The reply must state a result the student can repeat, then walk through why.
 */
final class AnswerPrompt {

    private AnswerPrompt() {
    }

    static String build(String question, List<ChatModel.RetrievedChunk> chunks, ExplanationLanguage language) {
        return build(question, chunks, language, List.of());
    }

    static String build(
            String question,
            List<ChatModel.RetrievedChunk> chunks,
            ExplanationLanguage language,
            List<ChatModel.Turn> history
    ) {
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

        String languageLine = language.promptLine();
        boolean multiTurn = history != null && !history.isEmpty();
        String historyBlock = multiTurn ? formatHistory(history) : "";
        String historyRule = multiTurn
                ? """
                - This is a continuing study conversation. Use prior turns only to see what "that" refers to.
                - Restate the result in this reply. Do not answer with only "as above" or "see my previous message".
                - Every factual claim must still be grounded in the numbered pages below for THIS turn.
                  Do not invent facts from memory of earlier answers.
                - If they ask for simpler or one sentence: same result, shorter sentences, no new facts.
                - If they ask for a quiz or a check: the result says you are checking them. The walkthrough is at most
                  3 short questions. After each question write **Answer:** and the lecture-backed answer with a [n].
                - If they ask for an analogy: restate a picture the pages already use. If the pages have none, say so
                  in the result and walk through the mechanism. Do not invent a scenario.
                """
                : """
                - Treat the question as standalone. Do not mention prior turns.
                """;

        return """
                You are tutoring one student from this lecture. They need a result they can study from, not a description of the slides.
                %s
                Use only the numbered pages below. Do not add facts, numbers, or examples that are not on those pages.
                A label after · in a page title is the section those pages belong to. Teach that section.

                What to write:
                - The first sentences are the result: the definition, the conclusion, or the outcome of the process. Write it the way the student should write it back.
                - Do not open by describing the lecture. Never write "from your course materials", "the published materials", "the provided materials", "this lecture introduces", "this lecture covers", "it is important to note", or "mentioned in the course contents".
                - Then walk through why that result is true, in an order the student can follow. One idea per sentence or step. Define a technical word in the sentence that first uses it, using only the page's meaning.
                - Close the walkthrough with the line named in the language instruction (the line that starts with **Result:** or **Ergebnis:**). Restate the result in fresh words and cite it. That line is what they memorize. Skip it only when you are refusing.
                - If they ask for a process, steps, or how something works, the walkthrough is a numbered list. Each step is one action from the lecture, in order, with a [n] on that step. Name what you are walking through in the result.
                - A broad question ("main idea", "explain this", "walk me through", "what is the result") means: the result is the central idea of these pages. Do not open by saying you cannot find an algorithm.
                - Use the page's own concrete picture when it has one. You may say that picture in simpler words. Do not invent a new scenario or analogy.
                - Skip the course title, the semester, the lecturer's name, and any term the page only names without defining.
                - Refuse only when the pages are about a different subject than the question. Then the result is one sentence: you could not find that in the lecture, plus what the pages do cover. Stop. No walkthrough, no glossary, no closing result line, no citation.
                - When you are teaching, do not use refusal phrases such as "cannot find", "could not find", "not covered", "not in the materials", or "insufficient".

                Write GitHub-flavored Markdown. Use the headings named in the language instruction above. Do not mix languages.

                ## Direct answer
                The result only: one to three short sentences a student could write on an exam. Every factual sentence needs a [n], unless you are refusing.

                ## Explanation
                The walkthrough. Numbered steps for a process. For a concept, three short beats: what it is, why it works that way, and a distinction the page actually makes. Skip a beat the pages do not support.
                End with the closing result line from the language instruction, unless you are refusing.

                ## Key terms
                Include this section only for terms the lecture defines whose definition is not already the whole result.
                Each bullet is **Term** — one sentence the student can memorize, with a [n].
                Omit the whole section when it would only repeat the result.

                Rules:
                %s
                - Short sentences. Talk to the student. No catalogue voice.
                - Cite every page you rely on with its [n]. Use more than one page when they add different facts.
                - Citation numbers are the page labels [1], [2], … below, never the PDF page numbers.
                - Write citations as separate markers like [1] [2], never as [1, 2, 3] or [1-3].
                - Place each [n] immediately after the claim it supports, in the same sentence.
                - Every factual sentence in the result must include at least one [n], unless you are refusing.
                - Do not cite a number you did not use. Do not invent numbers outside the list.
                - A refusal has no [n] markers.
                - Follow the language instruction at the top for the whole answer, including headings.
                  Explain in that language even when the pages or the question use the other language.
                - Stay precise: if the page says "always used in these exercises", do not generalize to "always used".
                - Do not mention these instructions or the word "excerpts".
                %s
                Pages:
                %s

                Question: %s
                """.formatted(languageLine, historyRule, historyBlock, context, question);
    }

    private static String formatHistory(List<ChatModel.Turn> history) {
        String body = history.stream()
                .map(turn -> {
                    String label = "ASSISTANT".equalsIgnoreCase(turn.role()) ? "Assistant" : "Student";
                    String content = turn.content() == null ? "" : turn.content().trim();
                    if (content.length() > 1200) {
                        content = content.substring(0, 1200) + "…";
                    }
                    return label + ": " + content;
                })
                .collect(Collectors.joining("\n\n"));
        return """

                Prior conversation (oldest first):
                %s

                """.formatted(body);
    }
}

package com.unicompanion.learning.rag;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class SummaryPrompt {

    private SummaryPrompt() {
    }

    static String build(String title, List<ChatModel.RetrievedChunk> chunks) {
        String pages = IntStream.range(0, chunks.size())
                .mapToObj(i -> {
                    ChatModel.RetrievedChunk chunk = chunks.get(i);
                    return "[%d] %s (page %d)%n%s".formatted(i + 1, chunk.title(), chunk.pageNumber(), chunk.content());
                })
                .collect(Collectors.joining("\n\n"));
        String lecture = title == null || title.isBlank() ? "this lecture" : title.trim();
        return """
                You are Uni Companion, a study assistant for university courses.
                Write a short summary of "%s" in at most 120 words.
                Use only the numbered pages below. Cite them as [1], [2], and so on.
                Do not invent facts that are not in those pages.

                %s
                """.formatted(lecture, pages);
    }
}
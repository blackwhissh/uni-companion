package com.unicompanion.learning.rag;

import com.google.auth.oauth2.GoogleCredentials;
import com.unicompanion.learning.config.GeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Vertex AI generateContent for grounded answers via ADC.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "unicompanion.gemini", name = "enabled", havingValue = "true")
public class VertexChatModel implements ChatModel {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final Logger log = LoggerFactory.getLogger(VertexChatModel.class);

    private final GeminiProperties properties;
    private final RestClient http;
    private final GoogleCredentials credentials;

    public VertexChatModel(GeminiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.http = restClientBuilder.build();
        try {
            this.credentials = GoogleCredentials.getApplicationDefault().createScoped(CLOUD_PLATFORM_SCOPE);
            log.info(
                    "Vertex chat model ready project={} location={} model={}",
                    properties.projectId(),
                    properties.location(),
                    properties.chatModel()
            );
        } catch (IOException ex) {
            throw new UncheckedIOException("Vertex AI credentials are not available. Run gcloud auth application-default login.", ex);
        }
    }

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
        long started = System.nanoTime();
        String prompt = AnswerPrompt.build(question, chunks);
        try {
            String text = generateContent(prompt, 0.25, 2048);
            log.debug(
                    "Vertex chat ok questionChars={} chunks={} answerChars={} ({} ms)",
                    question.length(),
                    chunks.size(),
                    text.length(),
                    (System.nanoTime() - started) / 1_000_000L
            );
            return text;
        } catch (RuntimeException ex) {
            log.error("Vertex chat failed questionChars={} chunks={}", question.length(), chunks.size(), ex);
            throw ex;
        }
    }

    @Override
    public List<FlashcardDraft> generateFlashcards(
            List<RetrievedChunk> chunks,
            int count,
            List<String> questionsToAvoid
    ) {
        if (chunks == null || chunks.isEmpty() || count <= 0) {
            return List.of();
        }
        long started = System.nanoTime();
        try {
            List<FlashcardDraft> parsed = generateAndParseJson(
                    FlashcardPrompt.build(chunks, count, questionsToAvoid),
                    16_384,
                    count,
                    FlashcardJsonParser::parse,
                    "flashcards"
            );
            log.debug(
                    "Vertex flashcards ok chunks={} cards={} ({} ms)",
                    chunks.size(),
                    parsed.size(),
                    (System.nanoTime() - started) / 1_000_000L
            );
            return parsed;
        } catch (RuntimeException ex) {
            log.error("Vertex flashcards failed chunks={}", chunks.size(), ex);
            throw ex;
        }
    }

    @Override
    public List<QuizQuestionDraft> generateQuiz(
            List<RetrievedChunk> chunks,
            int count,
            List<String> questionsToAvoid
    ) {
        if (chunks == null || chunks.isEmpty() || count <= 0) {
            return List.of();
        }
        long started = System.nanoTime();
        try {
            List<QuizQuestionDraft> parsed = generateAndParseJson(
                    QuizPrompt.build(chunks, count, questionsToAvoid),
                    16_384,
                    count,
                    QuizJsonParser::parse,
                    "quiz"
            );
            log.debug(
                    "Vertex quiz ok chunks={} questions={} ({} ms)",
                    chunks.size(),
                    parsed.size(),
                    (System.nanoTime() - started) / 1_000_000L
            );
            return parsed;
        } catch (RuntimeException ex) {
            log.error("Vertex quiz failed chunks={}", chunks.size(), ex);
            throw ex;
        }
    }

    private <T> List<T> generateAndParseJson(
            String prompt,
            int maxOutputTokens,
            int count,
            java.util.function.BiFunction<String, Integer, List<T>> parser,
            String kind
    ) {
        IllegalStateException unusable = null;
        String previousText = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String requestPrompt = attempt == 1 || previousText == null
                    ? prompt
                    : repairJsonPrompt(prompt, previousText, kind);
            double temperature = attempt == 1 ? 0.35 : 0.55;
            String text;
            try {
                text = generateJsonContent(requestPrompt, temperature, maxOutputTokens);
            } catch (IllegalStateException ex) {
                log.warn("Vertex {} empty response attempt={}", kind, attempt, ex);
                unusable = ex;
                continue;
            }
            log.info(
                    "Vertex {} raw attempt={} chars={} output={}",
                    kind,
                    attempt,
                    text.length(),
                    text.length() <= 4_000 ? text : text.substring(0, 4_000) + "…"
            );
            List<T> parsed = parser.apply(text, count);
            if (!parsed.isEmpty()) {
                return parsed;
            }
            previousText = text;
            log.warn(
                    "Vertex {} JSON unusable attempt={} chars={} preview={}",
                    kind,
                    attempt,
                    text.length(),
                    text.substring(0, Math.min(400, text.length()))
            );
            unusable = new IllegalStateException("Vertex " + kind + " returned no usable JSON.");
        }
        throw unusable;
    }

    private static String repairJsonPrompt(String originalPrompt, String previousOutput, String kind) {
        String preview = previousOutput.length() <= 2_500
                ? previousOutput
                : previousOutput.substring(0, 2_500) + "…";
        return """
                Your previous %s output was not usable JSON (empty parse or wrong shape).
                Return ONLY a valid JSON array matching the required schema. No markdown fences, no commentary.

                Previous output:
                %s

                Original instructions:
                %s
                """.formatted(kind, preview, originalPrompt);
    }

    private String generateContent(String prompt, double temperature, int maxOutputTokens) {
        return generateContent(prompt, temperature, maxOutputTokens, false);
    }

    private String generateJsonContent(String prompt, double temperature, int maxOutputTokens) {
        return generateContent(prompt, temperature, maxOutputTokens, true);
    }

    private String generateContent(String prompt, double temperature, int maxOutputTokens, boolean json) {
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", maxOutputTokens);
        if (json) {
            generationConfig.put("responseMimeType", "application/json");
        }
        GenerateResponse body = http.post()
                .uri(generateUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken())
                .body(Map.of(
                        "contents", List.of(Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", prompt))
                        )),
                        "generationConfig", generationConfig
                ))
                .retrieve()
                .body(GenerateResponse.class);
        String text = extractText(body);
        if (text.isBlank()) {
            throw new IllegalStateException("Vertex chat returned an empty response.");
        }
        return text;
    }

    private static String extractText(GenerateResponse body) {
        if (body == null || body.candidates() == null || body.candidates().isEmpty()) {
            return "";
        }
        GenerateResponse.Content content = body.candidates().getFirst().content();
        if (content == null || content.parts() == null) {
            return "";
        }
        return content.parts().stream()
                .map(GenerateResponse.Part::text)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"))
                .trim();
    }

    private String generateUrl() {
        String location = properties.location() == null || properties.location().isBlank() ? "global" : properties.location();
        String host = "global".equalsIgnoreCase(location)
                ? "https://aiplatform.googleapis.com"
                : "https://" + location + "-aiplatform.googleapis.com";
        return host + "/v1/projects/" + properties.projectId()
                + "/locations/" + location
                + "/publishers/google/models/" + properties.chatModel()
                + ":generateContent";
    }

    private String accessToken() {
        try {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private record GenerateResponse(List<Candidate> candidates) {
        private record Candidate(Content content) {
        }

        private record Content(List<Part> parts) {
        }

        private record Part(String text) {
        }
    }
}

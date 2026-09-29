package com.unicompanion.learning.ingestion;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vertex AI embeddings via the same publisher models endpoint used for generateContent.
 * Auth uses Application Default Credentials ({@code gcloud auth application-default login}).
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "unicompanion.gemini", name = "enabled", havingValue = "true")
public class VertexEmbeddingModel implements EmbeddingModel {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final int BATCH_SIZE = 16;
    private static final Logger log = LoggerFactory.getLogger(VertexEmbeddingModel.class);
    private final GeminiProperties properties;
    private final RestClient http;
    private final GoogleCredentials credentials;

    public VertexEmbeddingModel(GeminiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.http = restClientBuilder.build();
        try {
            this.credentials = GoogleCredentials.getApplicationDefault().createScoped(CLOUD_PLATFORM_SCOPE);
            log.info(
                    "Vertex embedding model ready project={} location={} model={}",
                    properties.projectId(),
                    properties.location(),
                    properties.embeddingModel()
            );
        } catch (IOException ex) {
            throw new UncheckedIOException("Vertex AI credentials are not available. Run gcloud auth application-default login.", ex);
        }
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text == null ? "" : text)).getFirst();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, texts.size());
            vectors.addAll(predictBatch(texts.subList(start, end)));
        }
        return List.copyOf(vectors);
    }

    private List<float[]> predictBatch(List<String> batch) {
        int chars = batch.stream().mapToInt(text -> text == null ? 0 : text.length()).sum();
        long started = System.nanoTime();
        try {
            List<float[]> vectors = QuotaAwareRetry.execute(() -> predictOnce(batch));
            log.debug(
                    "Vertex embed ok texts={} chars={} dims={} ({} ms)",
                    batch.size(),
                    chars,
                    DIMENSIONS,
                    (System.nanoTime() - started) / 1_000_000L
            );
            return vectors;
        } catch (RuntimeException ex) {
            log.error(
                    "Vertex embed failed texts={} chars={} ({} ms)",
                    batch.size(),
                    chars,
                    (System.nanoTime() - started) / 1_000_000L,
                    ex
            );
            throw QuotaAwareRetry.wrap(ex);
        }
    }

    private List<float[]> predictOnce(List<String> batch) {
        List<Map<String, Object>> instances = batch.stream()
                .map(text -> Map.<String, Object>of(
                        "content", text == null ? "" : text,
                        "task_type", "RETRIEVAL_DOCUMENT"
                ))
                .toList();
        PredictResponse body = http.post()
                .uri(predictUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken())
                .body(Map.of(
                        "instances", instances,
                        "parameters", Map.of("outputDimensionality", DIMENSIONS)
                ))
                .retrieve()
                .body(PredictResponse.class);

        if (body == null || body.predictions() == null || body.predictions().size() != batch.size()) {
            int count = body == null || body.predictions() == null ? 0 : body.predictions().size();
            throw EmbeddingException.failed(
                    "Vertex embedding returned " + count + " predictions, expected " + batch.size() + ".",
                    null
            );
        }
        List<float[]> vectors = new ArrayList<>(batch.size());
        for (Prediction prediction : body.predictions()) {
            List<Double> values = prediction == null || prediction.embeddings() == null
                    ? null
                    : prediction.embeddings().values();
            if (values == null || values.size() != DIMENSIONS) {
                throw EmbeddingException.failed(
                        "Vertex embedding size was " + (values == null ? 0 : values.size()) + ", expected " + DIMENSIONS + ".",
                        null
                );
            }
            float[] vector = new float[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) {
                vector[i] = values.get(i).floatValue();
            }
            vectors.add(vector);
        }
        return vectors;
    }
    private String predictUrl() {
        String location = properties.location() == null || properties.location().isBlank() ? "global" : properties.location();
        String host = "global".equalsIgnoreCase(location)
                ? "https://aiplatform.googleapis.com"
                : "https://" + location + "-aiplatform.googleapis.com";
        return host + "/v1/projects/" + properties.projectId()
                + "/locations/" + location
                + "/publishers/google/models/" + properties.embeddingModel()
                + ":predict";
    }

    private String accessToken() {
        try {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not refresh Vertex AI access token.", ex);
        }
    }

    private record PredictResponse(List<Prediction> predictions) {
    }

    private record Prediction(Embedding embeddings) {
    }

    private record Embedding(List<Double> values) {
    }
}

package com.unicompanion.learning.ingestion;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Deterministic stand-in so CI and local ingest reach READY without Vertex.
 * When {@code unicompanion.gemini.enabled=true}, {@link VertexEmbeddingModel} is {@code @Primary}.
 */
@Component
public class FakeEmbeddingModel implements EmbeddingModel {

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            Arrays.fill(vector, 0.01f);
            return vector;
        }
        for (int i = 0; i < DIMENSIONS; i++) {
            int value = Byte.toUnsignedInt(bytes[i % bytes.length]);
            vector[i] = ((value + i) % 256) / 255f;
        }
        return vector;
    }
}

package com.unicompanion.learning.ingestion;

import java.util.ArrayList;
import java.util.List;

public interface EmbeddingModel {

    int DIMENSIONS = 768;

    float[] embed(String text);

    default List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return List.copyOf(vectors);
    }
}

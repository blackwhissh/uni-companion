package com.unicompanion.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "unicompanion.gemini")
public record GeminiProperties(
        boolean enabled,
        String projectId,
        String location,
        String embeddingModel,
        String chatModel
) {
}

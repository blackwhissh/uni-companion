package com.unicompanion.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "unicompanion.ingest")
public record IngestProperties(boolean async) {
}

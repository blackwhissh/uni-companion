package com.unicompanion.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "unicompanion.rate-limit")
public record AiRateLimitProperties(boolean enabled, int requestsPerMinute, int burst) {
}

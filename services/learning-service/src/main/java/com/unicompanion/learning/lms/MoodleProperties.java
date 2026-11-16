package com.unicompanion.learning.lms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "unicompanion.lms.moodle")
public record MoodleProperties(
        boolean enabled,
        String baseUrl,
        String token
) {
    public MoodleProperties {
        if (baseUrl == null) {
            baseUrl = "";
        }
        if (token == null) {
            token = "";
        }
    }

    public String normalizedBaseUrl() {
        String value = baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}

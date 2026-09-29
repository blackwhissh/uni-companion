package com.unicompanion.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "unicompanion.storage")
public record StorageProperties(String path) {
}

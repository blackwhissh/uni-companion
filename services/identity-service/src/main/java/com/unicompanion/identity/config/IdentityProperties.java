package com.unicompanion.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "unicompanion")
public record IdentityProperties(Jwt jwt, Seed seed, List<String> corsAllowedOrigins) {

    public record Jwt(String secret, Duration ttl) {
    }

    public record Seed(String adminEmail, String adminPassword, String adminDisplayName) {
    }
}

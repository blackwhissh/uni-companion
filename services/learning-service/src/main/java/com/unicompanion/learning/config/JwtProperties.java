package com.unicompanion.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "unicompanion")
public record JwtProperties(Jwt jwt, List<String> corsAllowedOrigins) {

    public record Jwt(String secret) {
    }
}

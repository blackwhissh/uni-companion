package com.unicompanion.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "unicompanion.rag")
public record RagProperties(
        /** How many vector chunks to retrieve per Q&A query. */
        int topK,
        /** Sliding-window Q&A calls allowed per user per minute. */
        int requestsPerMinute,
        /** Rolling 24h Q&A calls allowed per user. */
        int requestsPerDay,
        /**
         * Demo switch. When true, {@code X-Uni-Force-Model-Down: true} skips the chat model
         * and returns retrieved passages. Off in production.
         */
        boolean allowModelDownHeader,
        /**
         * pgvector cosine distance ({@code <=>}) above which a neighbour is dropped.
         * {@code 0} disables the cutoff. The fake hash embedding must leave this at 0;
         * see ADR-0007.
         */
        double maxCosineDistance
) {
    public RagProperties {
        if (topK < 1) {
            topK = 1;
        }
        if (topK > 32) {
            topK = 32;
        }
        if (requestsPerMinute < 1) {
            requestsPerMinute = 1;
        }
        if (requestsPerDay < 1) {
            requestsPerDay = 1;
        }
        if (maxCosineDistance < 0) {
            maxCosineDistance = 0;
        }
        if (maxCosineDistance > 2) {
            maxCosineDistance = 2;
        }
    }
}

package com.unicompanion.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "unicompanion")
public record IdentityProperties(Jwt jwt, Seed seed, List<String> corsAllowedOrigins, Identity identity) {

    public IdentityProperties {
        if (identity == null) {
            identity = Identity.defaults();
        }
    }

    /**
     * University-email verification. Defaults fail closed: one campus domain, no code written to logs.
     */
    public record Identity(Verification verification) {
        public Identity {
            if (verification == null) {
                verification = Verification.defaults();
            }
        }

        static Identity defaults() {
            return new Identity(null);
        }
    }

    public record Verification(List<String> domains, Duration codeTtl, Integer maxAttempts, Boolean logCodes) {
        public Verification {
            if (domains == null || domains.isEmpty()) {
                domains = List.of("uni-siegen.de");
            }
            if (codeTtl == null || codeTtl.isZero() || codeTtl.isNegative()) {
                codeTtl = Duration.ofMinutes(15);
            }
            if (maxAttempts == null || maxAttempts < 1) {
                maxAttempts = 5;
            }
            if (logCodes == null) {
                logCodes = false;
            }
        }

        static Verification defaults() {
            return new Verification(null, null, null, null);
        }
    }

    public record Jwt(String secret, Duration ttl, Duration refreshTtl) {
        public Jwt {
            if (refreshTtl == null) {
                refreshTtl = Duration.ofDays(14);
            }
        }
    }

    public record Seed(String adminEmail, String adminPassword, String adminDisplayName) {
    }
}

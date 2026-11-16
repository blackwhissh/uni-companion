package com.unicompanion.identity.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Exact university-domain allowlist. {@code student@cs.uni-siegen.de} does not match {@code uni-siegen.de};
 * list every domain that should count.
 */
public final class UniversityEmailPolicy {

    private final Set<String> domains;

    public UniversityEmailPolicy(List<String> configured) {
        Set<String> normalized = new HashSet<>();
        if (configured != null) {
            for (String domain : configured) {
                String clean = normalizeDomain(domain);
                if (!clean.isEmpty()) {
                    normalized.add(clean);
                }
            }
        }
        this.domains = Set.copyOf(normalized);
    }

    public boolean accepts(String email) {
        String domain = domainOf(email);
        return domain != null && domains.contains(domain);
    }

    public static String domainOf(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        int at = trimmed.lastIndexOf('@');
        if (at <= 0 || at != trimmed.indexOf('@') || at == trimmed.length() - 1) {
            return null;
        }
        String domain = trimmed.substring(at + 1);
        if (domain.startsWith(".") || domain.endsWith(".") || domain.contains(" ") || !domain.contains(".")) {
            return null;
        }
        return domain;
    }

    private static String normalizeDomain(String domain) {
        if (domain == null) {
            return "";
        }
        String clean = domain.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("@")) {
            clean = clean.substring(1);
        }
        return clean;
    }
}

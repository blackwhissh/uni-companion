package com.unicompanion.learning.config;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores request/response pairs keyed by user + Idempotency-Key so retries do not double-create.
 */
@Service
public class IdempotencyService {

    private final JdbcTemplate jdbc;

    public IdempotencyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CachedResponse> begin(UUID userId, String rawKey, String route, String fingerprint) {
        String key = normalize(rawKey);
        if (key == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        try {
            jdbc.update(
                    """
                            insert into idempotency_keys
                              (user_id, idempotency_key, route, request_fingerprint, status, created_at, updated_at)
                            values (?, ?, ?, ?, 'PENDING', ?, ?)
                            """,
                    userId,
                    key,
                    route,
                    fingerprint,
                    Timestamp.from(now),
                    Timestamp.from(now)
            );
            return Optional.empty();
        } catch (DuplicateKeyException ex) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    """
                            select request_fingerprint, status, http_status, response_body
                            from idempotency_keys
                            where user_id = ? and idempotency_key = ?
                            """,
                    userId,
                    key
            );
            if (rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Could not reserve that Idempotency-Key. Try again.");
            }
            Map<String, Object> row = rows.getFirst();
            String existingFingerprint = String.valueOf(row.get("request_fingerprint"));
            if (!existingFingerprint.equals(fingerprint)) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Idempotency-Key was reused with a different request."
                );
            }
            String status = String.valueOf(row.get("status"));
            if ("PENDING".equals(status)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "That request is still in progress.");
            }
            if ("COMPLETED".equals(status) && row.get("http_status") != null && row.get("response_body") != null) {
                return Optional.of(new CachedResponse(
                        ((Number) row.get("http_status")).intValue(),
                        String.valueOf(row.get("response_body"))
                ));
            }
            // Previous attempt failed — allow a fresh run with the same key.
            jdbc.update(
                    """
                            update idempotency_keys
                            set status = 'PENDING', http_status = null, response_body = null, route = ?, updated_at = ?
                            where user_id = ? and idempotency_key = ?
                            """,
                    route,
                    Timestamp.from(Instant.now()),
                    userId,
                    key
            );
            return Optional.empty();
        }
    }

    public void complete(UUID userId, String rawKey, int httpStatus, String responseBody) {
        String key = normalize(rawKey);
        if (key == null) {
            return;
        }
        jdbc.update(
                """
                        update idempotency_keys
                        set status = 'COMPLETED', http_status = ?, response_body = ?, updated_at = ?
                        where user_id = ? and idempotency_key = ?
                        """,
                httpStatus,
                responseBody,
                Timestamp.from(Instant.now()),
                userId,
                key
        );
    }

    public void fail(UUID userId, String rawKey) {
        String key = normalize(rawKey);
        if (key == null) {
            return;
        }
        jdbc.update(
                """
                        update idempotency_keys
                        set status = 'FAILED', updated_at = ?
                        where user_id = ? and idempotency_key = ?
                        """,
                Timestamp.from(Instant.now()),
                userId,
                key
        );
    }

    public static String fingerprint(String... parts) {
        String joined = String.join("\n", parts);
        return sha256(joined.getBytes(StandardCharsets.UTF_8));
    }

    public static String fingerprintBytes(byte[] bytes) {
        return sha256(bytes == null ? new byte[0] : bytes);
    }

    private static String sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String normalize(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        String trimmed = rawKey.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 128 characters or fewer.");
        }
        return trimmed;
    }

    public record CachedResponse(int httpStatus, String body) {
    }
}

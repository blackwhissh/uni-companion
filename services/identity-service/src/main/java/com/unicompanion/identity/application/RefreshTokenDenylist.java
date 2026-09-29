package com.unicompanion.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory denylist for refresh tokens (by JWT id). Suitable for a single app instance.
 * Entries expire with the refresh token so the map stays bounded.
 */
@Service
public class RefreshTokenDenylist {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenDenylist.class);

    private final ConcurrentHashMap<String, Instant> revoked = new ConcurrentHashMap<>();

    public void revoke(Jwt refreshJwt) {
        if (refreshJwt == null) {
            return;
        }
        String id = refreshJwt.getId();
        Instant expires = refreshJwt.getExpiresAt();
        if (id == null || id.isBlank()) {
            // Tokens minted before jti was added — fall back to hashing the subject+issuedAt+token_use.
            id = fallbackId(refreshJwt);
        }
        if (expires == null) {
            expires = Instant.now().plusSeconds(86_400);
        }
        revoked.put(id, expires);
        purgeExpired();
        log.info("Revoked refresh token jti={} user={}", id, refreshJwt.getSubject());
    }

    public boolean isRevoked(Jwt refreshJwt) {
        if (refreshJwt == null) {
            return false;
        }
        purgeExpired();
        String id = refreshJwt.getId();
        if (id == null || id.isBlank()) {
            id = fallbackId(refreshJwt);
        }
        return revoked.containsKey(id);
    }

    private static String fallbackId(Jwt jwt) {
        Instant issued = jwt.getIssuedAt() == null ? Instant.EPOCH : jwt.getIssuedAt();
        return jwt.getSubject() + "|" + issued.getEpochSecond() + "|" + jwt.getClaimAsString("token_use");
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> it = revoked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Instant> entry = it.next();
            if (entry.getValue().isBefore(now)) {
                it.remove();
            }
        }
    }
}

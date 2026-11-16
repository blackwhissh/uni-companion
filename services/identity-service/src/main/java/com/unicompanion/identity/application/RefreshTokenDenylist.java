package com.unicompanion.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Persistent denylist for refresh tokens (by JWT id). Survives restarts and works across instances.
 */
@Service
public class RefreshTokenDenylist {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenDenylist.class);

    private final JdbcTemplate jdbc;

    public RefreshTokenDenylist(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void revoke(Jwt refreshJwt) {
        if (refreshJwt == null) {
            return;
        }
        String id = tokenId(refreshJwt);
        Instant expires = refreshJwt.getExpiresAt();
        if (expires == null) {
            expires = Instant.now().plusSeconds(86_400);
        }
        Instant now = Instant.now();
        jdbc.update(
                """
                insert into refresh_token_denylist (jti, expires_at, revoked_at)
                values (?, ?, ?)
                on conflict (jti) do update set expires_at = excluded.expires_at, revoked_at = excluded.revoked_at
                """,
                id,
                Timestamp.from(expires),
                Timestamp.from(now)
        );
        jdbc.update("delete from refresh_token_denylist where expires_at < ?", Timestamp.from(now));
        log.info("Revoked refresh token jti={} user={}", id, refreshJwt.getSubject());
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(Jwt refreshJwt) {
        if (refreshJwt == null) {
            return false;
        }
        String id = tokenId(refreshJwt);
        Instant now = Instant.now();
        Integer count = jdbc.queryForObject(
                """
                select count(*) from refresh_token_denylist
                where jti = ? and expires_at >= ?
                """,
                Integer.class,
                id,
                Timestamp.from(now)
        );
        return count != null && count > 0;
    }

    private static String tokenId(Jwt jwt) {
        String id = jwt.getId();
        if (id == null || id.isBlank()) {
            Instant issued = jwt.getIssuedAt() == null ? Instant.EPOCH : jwt.getIssuedAt();
            return jwt.getSubject() + "|" + issued.getEpochSecond() + "|" + jwt.getClaimAsString("token_use");
        }
        return id;
    }
}

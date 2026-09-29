package com.unicompanion.identity.application;

import com.unicompanion.identity.config.IdentityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final JwtEncoder jwtEncoder;
    private final IdentityProperties properties;

    public TokenService(JwtEncoder jwtEncoder, IdentityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public String issue(AccountView account) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.jwt().ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(account.id().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", account.email())
                .claim("roles", account.roles())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        log.debug("Issued JWT userId={} roles={} expiresAt={}", account.id(), account.roles(), expiresAt);
        return token;
    }
}

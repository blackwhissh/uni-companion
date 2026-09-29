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

    public static final String CLAIM_TOKEN_USE = "token_use";
    public static final String ACCESS = "access";
    public static final String REFRESH = "refresh";

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final JwtEncoder jwtEncoder;
    private final IdentityProperties properties;

    public TokenService(JwtEncoder jwtEncoder, IdentityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public String issueAccess(AccountView account) {
        return issue(account, ACCESS, properties.jwt().ttl());
    }

    public String issueRefresh(AccountView account) {
        return issue(account, REFRESH, properties.jwt().refreshTtl());
    }

    /** @deprecated use {@link #issueAccess(AccountView)} */
    public String issue(AccountView account) {
        return issueAccess(account);
    }

    private String issue(AccountView account, String tokenUse, java.time.Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(account.id().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", account.email())
                .claim("roles", account.roles())
                .claim(CLAIM_TOKEN_USE, tokenUse)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        log.debug("Issued {} JWT userId={} expiresAt={}", tokenUse, account.id(), expiresAt);
        return token;
    }
}

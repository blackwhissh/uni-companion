package com.unicompanion.identity.api;

import com.unicompanion.identity.application.AccountService;
import com.unicompanion.identity.application.AccountView;
import com.unicompanion.identity.application.RefreshTokenDenylist;
import com.unicompanion.identity.application.TokenService;
import com.unicompanion.identity.config.IdentityProperties;
import com.unicompanion.identity.domain.IdentityException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accounts;
    private final TokenService tokens;
    private final JwtDecoder jwtDecoder;
    private final IdentityProperties properties;
    private final RefreshTokenDenylist refreshDenylist;

    public AuthController(
            AccountService accounts,
            TokenService tokens,
            JwtDecoder jwtDecoder,
            IdentityProperties properties,
            RefreshTokenDenylist refreshDenylist) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
        this.refreshDenylist = refreshDenylist;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AccountView account = accounts.register(
                trim(request.email()),
                request.password(),
                request.displayName());
        return issueSession(account, httpRequest, httpResponse);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AccountView account = accounts.login(trim(request.email()), request.password());
        return issueSession(account, httpRequest, httpResponse);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = RefreshCookies.read(httpRequest);
        if (refreshToken == null) {
            throw IdentityException.unauthenticated();
        }
        try {
            Jwt jwt = jwtDecoder.decode(refreshToken);
            if (!TokenService.REFRESH.equals(jwt.getClaimAsString(TokenService.CLAIM_TOKEN_USE))) {
                throw IdentityException.unauthenticated();
            }
            if (refreshDenylist.isRevoked(jwt)) {
                RefreshCookies.clear(httpRequest, httpResponse);
                throw IdentityException.unauthenticated();
            }
            AccountView account = accounts.get(UUID.fromString(jwt.getSubject()));
            // Re-issue access (+ refresh cookie) without revoking the presented refresh token.
            // Revoking on every refresh breaks multi-tab sessions that refresh in parallel.
            return issueSession(account, httpRequest, httpResponse);
        } catch (JwtException | IllegalArgumentException ex) {
            RefreshCookies.clear(httpRequest, httpResponse);
            throw IdentityException.unauthenticated();
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = RefreshCookies.read(httpRequest);
        if (refreshToken != null) {
            try {
                Jwt jwt = jwtDecoder.decode(refreshToken);
                if (TokenService.REFRESH.equals(jwt.getClaimAsString(TokenService.CLAIM_TOKEN_USE))) {
                    refreshDenylist.revoke(jwt);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // Still clear the cookie below.
            }
        }
        RefreshCookies.clear(httpRequest, httpResponse);
    }

    private AuthResponse issueSession(
            AccountView account,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String access = tokens.issueAccess(account);
        String refresh = tokens.issueRefresh(account);
        RefreshCookies.set(httpRequest, httpResponse, refresh, properties.jwt().refreshTtl().toSeconds());
        return new AuthResponse(access, UserResponse.from(account));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

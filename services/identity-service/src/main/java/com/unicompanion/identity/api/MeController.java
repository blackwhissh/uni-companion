package com.unicompanion.identity.api;

import com.unicompanion.identity.application.AccountService;
import com.unicompanion.identity.application.RefreshTokenDenylist;
import com.unicompanion.identity.application.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AccountService accounts;
    private final JwtDecoder jwtDecoder;
    private final RefreshTokenDenylist refreshDenylist;

    public MeController(AccountService accounts, JwtDecoder jwtDecoder, RefreshTokenDenylist refreshDenylist) {
        this.accounts = accounts;
        this.jwtDecoder = jwtDecoder;
        this.refreshDenylist = refreshDenylist;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(accounts.get(UUID.fromString(jwt.getSubject())));
    }

    @PatchMapping("/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfilePatchRequest request) {
        return UserResponse.from(accounts.updateProfile(
                UUID.fromString(jwt.getSubject()),
                request.displayName(),
                request.interests()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        accounts.deleteAccount(UUID.fromString(jwt.getSubject()));
        String refreshToken = RefreshCookies.read(httpRequest);
        if (refreshToken != null) {
            try {
                Jwt refreshJwt = jwtDecoder.decode(refreshToken);
                if (TokenService.REFRESH.equals(refreshJwt.getClaimAsString(TokenService.CLAIM_TOKEN_USE))) {
                    refreshDenylist.revoke(refreshJwt);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // Still clear the cookie below.
            }
        }
        RefreshCookies.clear(httpRequest, httpResponse);
    }
}

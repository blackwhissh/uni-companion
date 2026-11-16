package com.unicompanion.identity.api;

import com.unicompanion.identity.application.AccountService;
import com.unicompanion.identity.application.EmailVerificationService;
import com.unicompanion.identity.application.RefreshTokenDenylist;
import com.unicompanion.identity.application.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AccountService accounts;
    private final EmailVerificationService verification;
    private final JwtDecoder jwtDecoder;
    private final RefreshTokenDenylist refreshDenylist;

    public MeController(
            AccountService accounts,
            EmailVerificationService verification,
            JwtDecoder jwtDecoder,
            RefreshTokenDenylist refreshDenylist
    ) {
        this.accounts = accounts;
        this.verification = verification;
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

    @PostMapping("/verification")
    public VerificationStatusResponse requestVerification(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest
    ) {
        EmailVerificationService.Requested requested = verification.request(
                UUID.fromString(jwt.getSubject()),
                CorrelationIdFilter.correlationId(httpRequest)
        );
        return new VerificationStatusResponse(requested.status(), requested.delivery());
    }

    @PostMapping("/verification/confirm")
    public UserResponse confirmVerification(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmVerificationRequest request,
            HttpServletRequest httpRequest
    ) {
        return UserResponse.from(verification.confirm(
                UUID.fromString(jwt.getSubject()),
                request.code(),
                CorrelationIdFilter.correlationId(httpRequest)
        ));
    }

    public record VerificationStatusResponse(String status, String delivery) {
    }

    public record ConfirmVerificationRequest(@NotBlank String code) {
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

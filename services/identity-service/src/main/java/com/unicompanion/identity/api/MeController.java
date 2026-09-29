package com.unicompanion.identity.api;

import com.unicompanion.identity.application.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AccountService accounts;

    public MeController(AccountService accounts) {
        this.accounts = accounts;
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
}

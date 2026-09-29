package com.unicompanion.identity.api;

import com.unicompanion.identity.application.AccountView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String interests,
        List<String> roles,
        Instant createdAt
) {

    public static UserResponse from(AccountView account) {
        return new UserResponse(
                account.id(),
                account.email(),
                account.displayName(),
                account.interests(),
                account.roles(),
                account.createdAt());
    }
}

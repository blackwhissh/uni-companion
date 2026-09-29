package com.unicompanion.identity.application;

import com.unicompanion.identity.domain.Role;
import com.unicompanion.identity.infrastructure.persistence.UserAccount;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountView(
        UUID id,
        String email,
        String displayName,
        String interests,
        List<String> roles,
        Instant createdAt
) {

    public static AccountView from(UserAccount account) {
        List<String> roles = account.getRoles().stream().map(Role::name).sorted().toList();
        return new AccountView(
                account.getId(),
                account.getEmail(),
                account.getDisplayName(),
                account.getInterests(),
                roles,
                account.getCreatedAt());
    }
}

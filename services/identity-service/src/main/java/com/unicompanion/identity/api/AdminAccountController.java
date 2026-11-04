package com.unicompanion.identity.api;

import com.unicompanion.identity.application.AccountService;
import com.unicompanion.identity.application.AccountView;
import com.unicompanion.identity.domain.IdentityException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AccountService accounts;

    public AdminAccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<AdminAccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        requirePlatformAdmin(jwt);
        return accounts.listDirectory().stream().map(AdminAccountResponse::from).toList();
    }

    @PutMapping("/{id}/course-admin")
    public AdminAccountResponse setCourseAdmin(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CourseAdminRequest request
    ) {
        requirePlatformAdmin(jwt);
        return AdminAccountResponse.from(accounts.setCourseAdmin(id, request.courseAdmin()));
    }

    private static void requirePlatformAdmin(Jwt jwt) {
        List<String> roles = jwt == null ? null : jwt.getClaimAsStringList("roles");
        if (roles == null || !roles.contains("ADMIN")) {
            throw IdentityException.forbidden();
        }
    }

    public record CourseAdminRequest(@NotNull Boolean courseAdmin) {
    }

    public record AdminAccountResponse(
            UUID id,
            String email,
            String displayName,
            List<String> roles,
            Instant createdAt
    ) {
        static AdminAccountResponse from(AccountView account) {
            return new AdminAccountResponse(
                    account.id(),
                    account.email(),
                    account.displayName(),
                    account.roles(),
                    account.createdAt());
        }
    }
}

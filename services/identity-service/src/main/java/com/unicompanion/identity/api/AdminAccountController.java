package com.unicompanion.identity.api;

import com.unicompanion.identity.application.AccountService;
import com.unicompanion.identity.application.AccountView;
import com.unicompanion.identity.application.AuditService;
import com.unicompanion.identity.domain.IdentityException;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AccountService accounts;
    private final AuditService audit;

    public AdminAccountController(AccountService accounts, AuditService audit) {
        this.accounts = accounts;
        this.audit = audit;
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
            @Valid @RequestBody CourseAdminRequest request,
            HttpServletRequest httpRequest
    ) {
        requirePlatformAdmin(jwt);
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountView updated = accounts.setCourseAdmin(id, request.courseAdmin());
        audit.record(
                actorId,
                request.courseAdmin() ? "COURSE_ADMIN_GRANTED" : "COURSE_ADMIN_REVOKED",
                "account",
                id.toString(),
                Map.of("courseAdmin", request.courseAdmin()),
                CorrelationIdFilter.correlationId(httpRequest)
        );
        return AdminAccountResponse.from(updated);
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

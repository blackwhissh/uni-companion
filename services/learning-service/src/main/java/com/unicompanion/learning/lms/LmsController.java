package com.unicompanion.learning.lms;

import com.unicompanion.learning.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/lms")
public class LmsController {

    private final LmsSyncService sync;

    public LmsController(LmsSyncService sync) {
        this.sync = sync;
    }

    @GetMapping
    public LmsLinkResponse get(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return LmsLinkResponse.from(sync.getLink(courseId, userId(jwt), isAdmin(jwt)));
    }

    @PutMapping
    public LmsLinkResponse upsert(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpsertLmsLinkRequest body,
            HttpServletRequest httpRequest
    ) {
        return LmsLinkResponse.from(sync.upsertLink(
                courseId,
                userId(jwt),
                isAdmin(jwt),
                body.provider(),
                body.externalCourseId(),
                body.enabled() == null || body.enabled(),
                CorrelationIdFilter.correlationId(httpRequest)
        ));
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.OK)
    public LmsSyncResponse runSync(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest
    ) {
        return LmsSyncResponse.from(sync.sync(
                courseId,
                userId(jwt),
                isAdmin(jwt),
                CorrelationIdFilter.correlationId(httpRequest)
        ));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static boolean isAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && (roles.contains("COURSE_ADMIN") || roles.contains("ADMIN"));
    }

    public record UpsertLmsLinkRequest(
            @NotNull LmsProvider provider,
            @NotBlank @Size(max = 128) String externalCourseId,
            Boolean enabled
    ) {
    }

    public record LmsLinkResponse(
            UUID courseId,
            LmsProvider provider,
            String externalCourseId,
            boolean enabled,
            Instant lastSyncedAt,
            String lastStatus,
            String lastMessage
    ) {
        static LmsLinkResponse from(CourseLmsLinkEntity link) {
            return new LmsLinkResponse(
                    link.getCourseId(),
                    link.getProvider(),
                    link.getExternalCourseId(),
                    link.isEnabled(),
                    link.getLastSyncedAt(),
                    link.getLastStatus(),
                    link.getLastMessage()
            );
        }
    }

    public record LmsSyncResponse(
            UUID courseId,
            LmsProvider provider,
            String externalCourseId,
            String status,
            int imported,
            int updated,
            int skipped,
            Instant syncedAt,
            List<String> materialIds,
            String message
    ) {
        static LmsSyncResponse from(LmsSyncResult result) {
            return new LmsSyncResponse(
                    result.courseId(),
                    result.provider(),
                    result.externalCourseId(),
                    result.status(),
                    result.imported(),
                    result.updated(),
                    result.skipped(),
                    result.syncedAt(),
                    result.materialIds(),
                    result.message()
            );
        }
    }
}

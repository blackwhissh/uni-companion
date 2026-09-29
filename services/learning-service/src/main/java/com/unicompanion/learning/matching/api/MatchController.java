package com.unicompanion.learning.matching.api;

import com.unicompanion.learning.matching.application.MatchingService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/match")
public class MatchController {

    private final MatchingService matching;

    public MatchController(MatchingService matching) {
        this.matching = matching;
    }

    @GetMapping("/courses/{courseId}/active-count")
    public MatchingService.MatchStatus activeCount(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return matching.status(courseId, userId(jwt));
    }

    @PutMapping("/courses/{courseId}/opt-in")
    public MatchingService.MatchStatus optIn(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OptInRequest request
    ) {
        return matching.setOptIn(courseId, userId(jwt), request.optedIn());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record OptInRequest(@NotNull Boolean optedIn) {
    }
}

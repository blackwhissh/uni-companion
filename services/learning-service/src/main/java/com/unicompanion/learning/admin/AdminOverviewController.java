package com.unicompanion.learning.admin;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminOverviewController {

    private final AdminOverviewService overview;

    public AdminOverviewController(AdminOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping("/courses")
    public List<AdminOverviewService.CourseSnapshot> courses(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt == null ? null : jwt.getClaimAsStringList("roles");
        if (roles == null || !roles.contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a platform admin can open the admin panel.");
        }
        return overview.courses();
    }
}

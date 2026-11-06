package com.unicompanion.identity.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OpenApiController {

    @GetMapping(path = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> apiDocs() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("openapi", "3.1.0");
        doc.put("info", Map.of(
                "title", "Uni Companion Identity Service API",
                "version", "1.0.0",
                "description", "Authentication, user profiles, GDPR account deletion, and platform admin role management."
        ));
        doc.put("components", Map.of(
                "securitySchemes", Map.of(
                        "bearerAuth", Map.of("type", "http", "scheme", "bearer", "bearerFormat", "JWT")
                )
        ));
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/api/auth/register", Map.of("post", operation("Register a student account", false)));
        paths.put("/api/auth/login", Map.of("post", operation("Authenticate and issue JWT + refresh cookie", false)));
        paths.put("/api/auth/refresh", Map.of("post", operation("Refresh access token from refresh cookie", false)));
        paths.put("/api/auth/logout", Map.of("post", operation("Revoke refresh token and clear cookie", false)));
        paths.put("/api/me", Map.of(
                "get", operation("Get current user profile", true),
                "delete", operation("Delete current student account (NFR-004)", true)
        ));
        paths.put("/api/me/profile", Map.of("patch", operation("Update display name and interests", true)));
        paths.put("/api/admin/accounts", Map.of("get", operation("List accounts for platform admin", true)));
        paths.put("/api/admin/accounts/{id}/course-admin", Map.of("put", operation("Grant or revoke COURSE_ADMIN role", true)));
        doc.put("paths", paths);
        return doc;
    }

    private static Map<String, Object> operation(String summary, boolean secured) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("summary", summary);
        if (secured) {
            op.put("security", List.of(Map.of("bearerAuth", List.of())));
        }
        op.put("responses", Map.of("200", Map.of("description", "OK")));
        return op;
    }
}

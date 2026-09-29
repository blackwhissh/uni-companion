package com.unicompanion.learning;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

final class JwtTestSupport {

    static final String SECRET = "test-secret-test-secret-test-secret";

    private JwtTestSupport() {
    }

    static String bearer(UUID userId, String email, String... roles) {
        return "Bearer " + token(userId, email, roles);
    }

    private static String token(UUID userId, String email, String... roles) {
        String header = base64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        long issuedAt = Instant.now().getEpochSecond();
        String roleJson = "[\"" + String.join("\",\"", roles) + "\"]";
        String payload = base64("""
                {"sub":"%s","email":"%s","roles":%s,"iat":%d,"exp":%d}
                """.formatted(userId, email, roleJson, issuedAt, issuedAt + 3600).replaceAll("\\s+", ""));
        String signingInput = header + "." + payload;
        return signingInput + "." + sign(signingInput);
    }

    private static String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}

package com.unicompanion.learning.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-user sliding-window limiter for expensive AI endpoints (RAG + study generation).
 * Registered inside the Spring Security filter chain so the JWT subject is available.
 */
public class AiRateLimitFilter extends OncePerRequestFilter {

    private final AiRateLimitProperties properties;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public AiRateLimitFilter(AiRateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.startsWith("/api/rag/")
                || path.contains("/flashcards/generate")
                || path.contains("/quizzes/generate"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String key = clientKey(request);
        Instant now = Instant.now();
        purge(now);
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        synchronized (window) {
            if (window.started.isBefore(now.minusSeconds(60))) {
                window.started = now;
                window.count.set(0);
            }
            int next = window.count.incrementAndGet();
            int limit = Math.max(properties.requestsPerMinute(), properties.burst());
            if (next > limit) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many AI requests. Wait a minute and try again.\"}"
                );
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return "user:" + jwt.getSubject();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void purge(Instant now) {
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Window> entry = it.next();
            if (entry.getValue().started.isBefore(now.minusSeconds(120))) {
                it.remove();
            }
        }
    }

    private static final class Window {
        private Instant started;
        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant started) {
            this.started = started;
        }
    }
}

package com.unicompanion.identity.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs after Spring Security so the JWT subject is still available when the response is logged.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestAccessLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (shouldLog(request)) {
                long ms = (System.nanoTime() - started) / 1_000_000L;
                log.info(
                        "HTTP {} {} -> {} ({} ms) user={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        ms,
                        currentUser()
                );
            }
        }
    }

    private static boolean shouldLog(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/");
    }

    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return "-";
        }
        return jwt.getSubject();
    }
}

package com.unicompanion.learning.config;

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
        } catch (Exception ex) {
            long ms = (System.nanoTime() - started) / 1_000_000L;
            log.error(
                    "HTTP {} {} failed after {} ms user={} — {}",
                    request.getMethod(),
                    requestPath(request),
                    ms,
                    currentUser(),
                    ex.toString(),
                    ex
            );
            throw ex;
        } finally {
            logCompleted(request, response, started);
        }
    }

    private void logCompleted(HttpServletRequest request, HttpServletResponse response, long started) {
        if (!shouldLog(request)) {
            return;
        }
        long ms = (System.nanoTime() - started) / 1_000_000L;
        int status = response.getStatus();
        String message = "HTTP {} {} -> {} ({} ms) user={}";
        Object[] args = {request.getMethod(), requestPath(request), status, ms, currentUser()};
        if (status >= 500) {
            log.error(message, args);
        } else if (status >= 400) {
            log.warn(message, args);
        } else {
            log.info(message, args);
        }
    }

    private static boolean shouldLog(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/");
    }

    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return query == null || query.isBlank() ? uri : uri + "?" + query;
    }

    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return "-";
        }
        return jwt.getSubject();
    }
}

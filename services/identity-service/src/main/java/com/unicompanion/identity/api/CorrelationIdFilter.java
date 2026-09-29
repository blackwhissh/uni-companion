package com.unicompanion.identity.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = "correlationId";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String id = correlationId(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        MDC.put(MDC_KEY, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String correlationId(HttpServletRequest request) {
        Object current = request.getAttribute(ATTRIBUTE);
        if (current instanceof String value && !value.isBlank()) {
            return value;
        }
        return correlationId(request.getHeader(HEADER));
    }

    private static String correlationId(String incoming) {
        if (incoming != null && incoming.matches("[A-Za-z0-9-]{1,64}")) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}

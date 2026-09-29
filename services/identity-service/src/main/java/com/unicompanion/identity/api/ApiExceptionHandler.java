package com.unicompanion.identity.api;

import com.unicompanion.identity.domain.IdentityException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IdentityException.class)
    ResponseEntity<ErrorResponse> identity(IdentityException ex, HttpServletRequest request) {
        HttpStatus status = status(ex.code());
        if (status == HttpStatus.UNAUTHORIZED) {
            log.warn("Auth rejected: {} on {} {}", ex.code(), request.getMethod(), request.getRequestURI());
        } else {
            log.info("Business rule {}: {} {}", ex.code(), request.getMethod(), request.getRequestURI());
        }
        return ResponseEntity.status(status).body(body(ex.code().name(), ex.getMessage(), request, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> details.put(error.getField(), error.getDefaultMessage()));
        log.warn("Validation failed on {} {}: {}", request.getMethod(), request.getRequestURI(), details.keySet());
        return ResponseEntity.badRequest().body(body("VALIDATION_FAILED", "Request validation failed.", request, details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpServletRequest request) {
        log.warn("Invalid JSON on {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.badRequest().body(body("INVALID_JSON", "Request body is not valid JSON.", request, null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(body("INTERNAL_ERROR", "Something went wrong.", request, null));
    }

    private static HttpStatus status(IdentityException.Code code) {
        return switch (code) {
            case WEAK_PASSWORD -> HttpStatus.BAD_REQUEST;
            case EMAIL_TAKEN -> HttpStatus.CONFLICT;
            case INVALID_CREDENTIALS, UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
        };
    }

    private static ErrorResponse body(String code, String message, HttpServletRequest request, Map<String, String> details) {
        return new ErrorResponse(code, message, CorrelationIdFilter.correlationId(request), details);
    }
}

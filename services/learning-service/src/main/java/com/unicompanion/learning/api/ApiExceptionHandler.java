package com.unicompanion.learning.api;

import com.unicompanion.learning.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> status(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String reason = ex.getReason() == null || ex.getReason().isBlank() ? status.getReasonPhrase() : ex.getReason();
        if (status.is5xxServerError()) {
            log.error(
                    "Request failed with {} on {} {} — {}",
                    status.value(),
                    request.getMethod(),
                    request.getRequestURI(),
                    reason,
                    ex
            );
        } else {
            log.warn(
                    "Request rejected with {} on {} {} — {}",
                    status.value(),
                    request.getMethod(),
                    request.getRequestURI(),
                    reason,
                    ex
            );
        }
        return ResponseEntity.status(status).body(body(status.name(), reason, request, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> details.put(error.getField(), error.getDefaultMessage()));
        log.warn(
                "Validation failed on {} {} — fields={}",
                request.getMethod(),
                request.getRequestURI(),
                details,
                ex
        );
        String message = details.size() == 1
                ? details.values().iterator().next()
                : "Request validation failed.";
        return ResponseEntity.badRequest().body(body("VALIDATION_FAILED", message, request, details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn(
                "Invalid JSON on {} {} — {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMostSpecificCause().getMessage(),
                ex
        );
        return ResponseEntity.badRequest().body(body("INVALID_JSON", "Request body is not valid JSON.", request, null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Upload too large on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(body("UPLOAD_TOO_LARGE", "The uploaded file is too large.", request, null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error(
                "Unhandled error on {} {} — {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.toString(),
                ex
        );
        return ResponseEntity.internalServerError().body(body("INTERNAL_ERROR", "Something went wrong.", request, null));
    }

    private static ErrorResponse body(String code, String message, HttpServletRequest request, Map<String, String> details) {
        return new ErrorResponse(code, message, CorrelationIdFilter.correlationId(request), details);
    }

    public record ErrorResponse(String code, String message, String correlationId, Map<String, String> details) {
    }
}

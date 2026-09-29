package com.jobmatching.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Normalizes every error response to API_DOCUMENTATION.md's {"error": "message"} shape,
 * in place of Spring's default RFC 7807 problem-detail body for ResponseStatusException.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders()).body(Map.of("error", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(401).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    // A path variable or query param that doesn't convert, e.g. /api/candidates/not-a-uuid
    // or ?limit=abc. Not an ErrorResponse, so it needs its own handler. The rejected value
    // isn't echoed back.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() != null ? " (expected " + ex.getRequiredType().getSimpleName() + ")" : "";
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid value for '" + ex.getName() + "'" + expected));
    }

    // Malformed JSON, a missing body, or a field of the wrong type. Also not an
    // ErrorResponse. Parser details stay in the log rather than the response.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", "Malformed or missing request body"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        // Spring MVC's own exceptions carry the right status: 400 missing parameter, 404
        // unknown path, 405 wrong method, 413 upload too large, 415 wrong content type, and
        // so on. @ExceptionHandler can't target the ErrorResponse interface directly, so
        // it's checked here, keeping 500 for everything else.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            if (status.is5xxServerError()) {
                log.error("Server error", ex);
            }
            // Spring's own wording for an unknown path is "No static resource ...", which
            // reads oddly for an API.
            String detail = ex instanceof NoResourceFoundException ? "Not found" : errorResponse.getBody().getDetail();
            return ResponseEntity.status(status)
                    .headers(errorResponse.getHeaders())
                    .body(Map.of("error", detail != null ? detail : "Request failed"));
        }
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
    }
}

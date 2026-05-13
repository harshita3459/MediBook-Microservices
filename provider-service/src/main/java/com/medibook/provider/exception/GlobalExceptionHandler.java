package com.medibook.provider.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler for the provider-service.
 *
 * Catches exceptions thrown anywhere in the controller layer and converts
 * them into structured JSON error responses — no stack traces leaked to clients.
 *
 * Response shape:
 * {
 *   "timestamp": "2026-04-20T10:30:00",
 *   "status":    404,
 *   "error":     "Not Found",
 *   "message":   "Provider not found with ID: 42"
 * }
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @ExceptionHandler(ProviderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProviderNotFound(ProviderNotFoundException ex) {
        log.warn("ProviderNotFoundException: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────

    @ExceptionHandler(ProviderAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleProviderAlreadyExists(ProviderAlreadyExistsException ex) {
        log.warn("ProviderAlreadyExistsException: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // ── 400 Bad Request — @Valid bean validation failures ─────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("Validation failed: {}", fieldErrors);

        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "One or more fields failed validation",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ── 400 Bad Request — malformed request params / illegal args ─────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ── 500 Internal Server Error — catch-all ─────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                null
        );
        return ResponseEntity.status(status).body(body);
    }

    // ── ErrorResponse record ──────────────────────────────────────────────────

    /**
     * Immutable error payload sent back to the client.
     * {@code fieldErrors} is only populated for validation failures (HTTP 400).
     */
    public record ErrorResponse(
            LocalDateTime timestamp,
            int           status,
            String        error,
            String        message,
            Map<String, String> fieldErrors
    ) {}
}
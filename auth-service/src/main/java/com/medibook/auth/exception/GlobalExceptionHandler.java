package com.medibook.auth.exception;
 
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
 
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
 
    // ── Validation Errors ──────────────────────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message   = error.getDefaultMessage();
            fieldErrors.put(fieldName, message);
        });
 
        return buildErrorResponse(HttpStatus.BAD_REQUEST,
            "Validation failed", fieldErrors);
    }
 
    // ── Business Logic Errors ──────────────────────────────────────────────────
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(
            UserAlreadyExistsException ex) {
        log.warn("User already exists: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }
 
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(
            InvalidCredentialsException ex) {
        log.warn("Invalid credentials: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), null);
    }
 
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException ex) {
        log.error("Runtime exception: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred", null);
    }
 
    // ── Helper ─────────────────────────────────────────────────────────────────
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String message, Object details) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);
        if (details != null) body.put("details", details);
        return ResponseEntity.status(status).body(body);
    }
}
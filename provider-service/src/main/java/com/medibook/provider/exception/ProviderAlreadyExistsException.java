package com.medibook.provider.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when attempting to register a provider profile for a userId
 * that already has one. Maps to HTTP 409 Conflict via GlobalExceptionHandler.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ProviderAlreadyExistsException extends RuntimeException {

    public ProviderAlreadyExistsException(String message) {
        super(message);
    }

    public ProviderAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProviderAlreadyExistsException(Long userId) {
        super("A provider profile already exists for userId: " + userId);
    }
}

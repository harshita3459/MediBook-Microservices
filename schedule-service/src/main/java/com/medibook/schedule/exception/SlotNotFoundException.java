package com.medibook.schedule.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a slot ID does not exist in the database.
 * @ResponseStatus maps this to HTTP 404 Not Found automatically.
 * GlobalExceptionHandler also catches this for a richer JSON response body.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class SlotNotFoundException extends RuntimeException {

    public SlotNotFoundException(String message) {
        super(message);
    }

    public SlotNotFoundException(Long slotId) {
        super("Slot not found with ID: " + slotId);
    }
}
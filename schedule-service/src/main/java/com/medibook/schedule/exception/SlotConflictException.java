package com.medibook.schedule.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a new slot overlaps in time with an existing slot
 * for the same provider on the same date.
 *
 * Example: Provider already has 10:00–10:30.
 * Trying to add 10:15–10:45 → SlotConflictException.
 *
 * Maps to HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class SlotConflictException extends RuntimeException {

    public SlotConflictException(String message) {
        super(message);
    }
}
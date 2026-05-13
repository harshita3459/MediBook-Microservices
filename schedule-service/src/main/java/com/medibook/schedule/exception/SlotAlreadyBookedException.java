package com.medibook.schedule.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown in two scenarios:
 *
 * Scenario A — explicit check:
 *   The slot already has isBooked=true when we try to book it.
 *
 * Scenario B — optimistic lock collision:
 *   Two patients tried to book the same slot simultaneously.
 *   Hibernate detected the version mismatch and threw
 *   ObjectOptimisticLockingFailureException, which we catch in
 *   ScheduleServiceImpl.bookSlot() and rethrow as this exception.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class SlotAlreadyBookedException extends RuntimeException {

    public SlotAlreadyBookedException(String message) {
        super(message);
    }

    public SlotAlreadyBookedException(Long slotId) {
        super("Slot " + slotId + " is already booked. Please choose a different slot.");
    }
}
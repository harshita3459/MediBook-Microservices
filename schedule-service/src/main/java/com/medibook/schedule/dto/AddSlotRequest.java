package com.medibook.schedule.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * DTO for adding a single availability slot.
 * Used in POST /api/v1/slots/add
 *
 * We use a DTO (not the entity directly) so:
 *   1. We control exactly what fields the client sends
 *   2. Validation annotations live here, keeping the entity clean
 *   3. Internal fields like version, createdAt are never exposed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddSlotRequest {

    @NotNull(message = "Provider ID is required")
    private Long providerId;

    // @Future ensures the date is tomorrow or later — can't add slots for today or past
    @NotNull(message = "Slot date is required")
    @Future(message = "Slot date must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private LocalDate date;

    @NotNull(message = "Start time is required")
    @JsonFormat(pattern = "HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    @JsonFormat(pattern = "HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalTime endTime;

    /**
     * Optional recurrence label stored on the slot for display purposes.
     * Values: "NONE", "DAILY", "WEEKDAYS", "WEEKLY", "MON_WED_FRI", "TUE_THU", "WEEKENDS"
     * Default is "NONE" when not provided.
     */
    private String recurrence;
}
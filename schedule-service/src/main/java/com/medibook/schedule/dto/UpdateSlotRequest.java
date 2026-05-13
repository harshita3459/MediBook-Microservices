package com.medibook.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * DTO for updating an existing slot's date or time window.
 * Used in PUT /api/v1/slots/{id}
 *
 * All fields are optional — only non-null fields are applied.
 * This gives PATCH-style semantics (update only what you send).
 *
 * Note: Updating a BOOKED slot is rejected by the service layer.
 * The patient must cancel their appointment first.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSlotRequest {

    // null = "don't change this field"
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private LocalDate date;
    @JsonFormat(pattern = "HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalTime startTime;
    @JsonFormat(pattern = "HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalTime endTime;
}
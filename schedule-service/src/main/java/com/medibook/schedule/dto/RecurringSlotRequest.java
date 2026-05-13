package com.medibook.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * DTO for generating recurring slots across a date range.
 * Used in POST /api/v1/slots/recurring
 *
 * Example — generate 30-min slots every Mon/Wed/Fri in May:
 * {
 *   "providerId":      1,
 *   "startDate":       "2026-05-01",
 *   "endDate":         "2026-05-31",
 *   "startTime":       "09:00",
 *   "endTime":         "17:00",
 *   "durationMinutes": 30,
 *   "recurrence":      "MON_WED_FRI"
 * }
 *
 * This single request creates ~200 slots automatically.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringSlotRequest {

    @NotNull(message = "Provider ID is required")
    private Long providerId;

    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private LocalDate endDate;

    @NotNull(message = "Start time is required")
    @JsonFormat(pattern = "HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    @JsonFormat(pattern = "HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalTime endTime;

    /**
     * Length of each individual slot in minutes.
     * Min 10 (very short consultations) — Max 480 (8-hour block).
     */
    @Min(value = 10,  message = "Duration must be at least 10 minutes")
    @Max(value = 480, message = "Duration cannot exceed 480 minutes (8 hours)")
    private int durationMinutes;

    /**
     * Supported values:
     *   DAILY       → every day including weekends
     *   WEEKDAYS    → Monday to Friday
     *   WEEKLY      → every Monday (same day every week)
     *   MON_WED_FRI → classic alternate-day clinic schedule
     *   TUE_THU     → complementary alternate-day schedule
     *   WEEKENDS    → Saturday and Sunday only
     */
    @NotBlank(message = "Recurrence pattern is required")
    private String recurrence;
}
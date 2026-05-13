package com.medibook.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO returned after generating recurring slots.
 * Instead of returning hundreds of slot objects (which would flood the response),
 * we return a summary with count and date range.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringSlotResponse {

    private String    message;
    private int       slotsCreated;
    private String    recurrencePattern;
    private LocalDate startDate;
    private LocalDate endDate;
    private String    timeWindow;     // e.g. "09:00 - 17:00"
    private int       durationMinutes;

    // Sample of first 5 created slots — lets frontend verify the pattern is correct
    private List<SlotResponse> sampleSlots;
}
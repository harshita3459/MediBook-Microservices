package com.medibook.schedule.dto;

import com.medibook.schedule.entity.AvailabilitySlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * DTO returned in API responses for slot data.
 *
 * WHY a separate response DTO instead of returning the entity directly?
 *   1. The entity has @Version (internal locking field) — clients don't need to see this
 *   2. We can add computed/derived fields (like "status" label) without changing the DB
 *   3. Decouples the API contract from the database schema
 *   4. Prevents accidentally exposing sensitive internal fields
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotResponse {

    private Long      slotId;
    private Long      providerId;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private LocalDate slotDate;
    @JsonFormat(pattern = "HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalTime startTime;
    @JsonFormat(pattern = "HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalTime endTime;
    private Integer   durationMinutes;
    private boolean   isBooked;
    private boolean   isBlocked;
    private String    recurrence;
    private Long      appointmentId;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime createdAt;

    /**
     * Human-readable status derived from isBooked and isBlocked flags.
     * Makes it easy for the frontend to display the right badge color:
     *   AVAILABLE → green
     *   BOOKED    → blue
     *   BLOCKED   → grey
     */
    private String status;

    /**
     * Static factory method — converts entity to response DTO.
     * Centralises the mapping so it's only written once.
     */
    public static SlotResponse from(AvailabilitySlot slot) {
        // Derive the status label from boolean flags
        String status;
        if (slot.isBooked()) {
            status = "BOOKED";
        } else if (slot.isBlocked()) {
            status = "BLOCKED";
        } else {
            status = "AVAILABLE";
        }

        return SlotResponse.builder()
                .slotId(slot.getSlotId())
                .providerId(slot.getProviderId())
                .slotDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .durationMinutes(slot.getDurationMinutes())
                .isBooked(slot.isBooked())
                .isBlocked(slot.isBlocked())
                .recurrence(slot.getRecurrence())
                .appointmentId(slot.getAppointmentId())
                .createdAt(slot.getCreatedAt())
                .status(status)
                .build();
    }
}
package com.medibook.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/** Request body for PUT /api/v1/appointments/{id}/reschedule */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RescheduleRequest {

    /** The new slot to move this appointment to (must be for the same provider) */
    @NotNull(message = "New slot ID is required")
    private Long newSlotId;
}

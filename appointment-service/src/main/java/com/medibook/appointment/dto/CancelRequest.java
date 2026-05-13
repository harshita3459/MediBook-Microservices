package com.medibook.appointment.dto;

import lombok.*;

/** Request body for PUT /api/v1/appointments/{id}/cancel */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CancelRequest {
    /** Optional reason for cancellation — stored for audit */
    private String reason;
}
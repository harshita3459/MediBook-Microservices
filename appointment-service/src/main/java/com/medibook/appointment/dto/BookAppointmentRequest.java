package com.medibook.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/** Request body for POST /api/v1/appointments — book a new appointment */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BookAppointmentRequest {

    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotNull(message = "Provider ID is required")
    private Long providerId;

    @NotNull(message = "Slot ID is required")
    private Long slotId;

    /** e.g. "CONSULTATION", "FOLLOW_UP", "EMERGENCY" */
    @NotBlank(message = "Service type is required")
    @Pattern(regexp = "^(CONSULTATION|FOLLOW_UP|EMERGENCY|LAB_REVIEW)$",
            message = "Service type must be CONSULTATION, FOLLOW_UP, EMERGENCY or LAB_REVIEW")
    private String serviceType;

    /** "IN_PERSON" or "TELECONSULTATION" */
    @NotBlank(message = "Mode of consultation is required")
    @Pattern(regexp = "^(IN_PERSON|TELECONSULTATION)$",
            message = "Mode of consultation must be IN_PERSON or TELECONSULTATION")
    private String modeOfConsultation;

    /** Optional patient notes about symptoms */
    @Size(max = 1000, message = "Patient notes must be under 1000 characters")
    private String patientNotes;
}

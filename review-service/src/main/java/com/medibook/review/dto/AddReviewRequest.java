package com.medibook.review.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Request body for POST /api/v1/reviews — submit a review.
 * Patient can only review after appointment is COMPLETED.
 */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AddReviewRequest {

    @NotNull(message = "Appointment ID is required")
    private Long appointmentId;

    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotNull(message = "Provider ID is required")
    private Long providerId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must not exceed 5")
    private Integer rating;

    /** Optional written review — can be null for star-only reviews */
    private String comment;

    /** Submit anonymously — name hidden in public view */
    private Boolean isAnonymous = false;
}

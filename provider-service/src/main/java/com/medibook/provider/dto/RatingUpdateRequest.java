package com.medibook.provider.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Sent by review-service after a new review is submitted.
 * review-service computes the new average and calls us to update the Provider record.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingUpdateRequest {
 
    @DecimalMin(value = "0.0") @DecimalMax(value = "5.0")
    private double newAvgRating;
 
    @Min(0)
    private int totalReviews;
}
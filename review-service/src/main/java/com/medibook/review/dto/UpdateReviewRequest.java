package com.medibook.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

/** Request body for PUT /api/v1/reviews/{id} — update existing review */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateReviewRequest {

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must not exceed 5")
    private Integer rating;

    private String comment;

    private Boolean isAnonymous;
}

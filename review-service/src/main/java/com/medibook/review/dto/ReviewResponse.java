package com.medibook.review.dto;

import com.medibook.review.entity.Review;
import lombok.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Outbound DTO — returned by all review endpoints */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ReviewResponse {

    private Long reviewId;
    private Long appointmentId;
    private Long patientId;
    private Long providerId;
    private Integer rating;
    private String comment;
    private Boolean isVerified;
    private Boolean isAnonymous;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime reviewDate;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime updatedAt;

    public static ReviewResponse from(Review r) {
        return ReviewResponse.builder()
                .reviewId(r.getReviewId())
                .appointmentId(r.getAppointmentId())
                .patientId(r.getPatientId())
                .providerId(r.getProviderId())
                .rating(r.getRating())
                .comment(r.getComment())
                .isVerified(r.getIsVerified())
                .isAnonymous(r.getIsAnonymous())
                .reviewDate(r.getReviewDate())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}

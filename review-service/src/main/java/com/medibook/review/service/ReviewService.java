package com.medibook.review.service;

import com.medibook.review.dto.*;
import java.util.List;

public interface ReviewService {
    ReviewResponse addReview(AddReviewRequest request);
    ReviewResponse getByAppointment(Long appointmentId);
    List<ReviewResponse> getByProvider(Long providerId);
    List<ReviewResponse> getByPatient(Long patientId);
    ReviewResponse updateReview(Long reviewId, UpdateReviewRequest request);
    void deleteReview(Long reviewId);
    Double getAvgRating(Long providerId);
    Long getReviewCount(Long providerId);
    List<ReviewResponse> getAllReviews();
}

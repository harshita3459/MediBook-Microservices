package com.medibook.review.service;

import com.medibook.review.dto.*;
import com.medibook.review.entity.Review;
import com.medibook.review.exception.*;
import com.medibook.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * ReviewServiceImpl — handles review submission and moderation.
 *
 * addReview flow:
 *   1. Verify no duplicate review for this appointmentId
 *   2. Verify appointment is COMPLETED (calls appointment-service)
 *   3. Save review record
 *   4. Recompute average rating
 *   5. Update provider-service avgRating (best-effort)
 *
 * deleteReview flow:
 *   1. Delete review (admin or patient owner)
 *   2. Recompute and push updated avgRating to provider-service
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final RestTemplate restTemplate;

    @Value("${services.appointment-url}")
    private String appointmentUrl;

    @Value("${services.provider-url}")
    private String providerUrl;

    // ── ADD ───────────────────────────────────────────────────────────────────

    @Override
    public ReviewResponse addReview(AddReviewRequest req) {
        log.info("Adding review: appointmentId={} patientId={} rating={}",
                req.getAppointmentId(), req.getPatientId(), req.getRating());

        // One review per appointment rule
        if (reviewRepository.existsByAppointmentId(req.getAppointmentId())) {
            throw new ReviewAlreadyExistsException(
                "A review already exists for appointmentId: " + req.getAppointmentId());
        }

        // Verify appointment is COMPLETED (non-blocking on failure — log and proceed)
        verifyAppointmentCompleted(req.getAppointmentId());

        Review review = Review.builder()
                .appointmentId(req.getAppointmentId())
                .patientId(req.getPatientId())
                .providerId(req.getProviderId())
                .rating(req.getRating())
                .comment(req.getComment())
                .isAnonymous(req.getIsAnonymous() != null && req.getIsAnonymous())
                .build();

        Review saved = reviewRepository.save(review);
        log.info("Review saved: reviewId={}", saved.getReviewId());

        // Update provider average rating
        pushAvgRatingToProvider(req.getProviderId());

        return ReviewResponse.from(saved);
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getByAppointment(Long appointmentId) {
        return ReviewResponse.from(
            reviewRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ReviewNotFoundException(
                    "No review found for appointmentId: " + appointmentId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getByProvider(Long providerId) {
        return reviewRepository.findByProviderIdOrderByReviewDateDesc(providerId)
                .stream().map(ReviewResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getByPatient(Long patientId) {
        return reviewRepository.findByPatientIdOrderByReviewDateDesc(patientId)
                .stream().map(ReviewResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getAllReviews() {
        return reviewRepository.findAllByOrderByReviewDateDesc()
                .stream().map(ReviewResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Double getAvgRating(Long providerId) {
        return reviewRepository.avgRatingByProviderId(providerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getReviewCount(Long providerId) {
        return reviewRepository.countByProviderId(providerId);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Override
    public ReviewResponse updateReview(Long reviewId, UpdateReviewRequest req) {
        Review review = findOrThrow(reviewId);

        if (req.getRating() != null)    review.setRating(req.getRating());
        if (req.getComment() != null)   review.setComment(req.getComment());
        if (req.getIsAnonymous() != null) review.setIsAnonymous(req.getIsAnonymous());

        Review updated = reviewRepository.save(review);
        log.info("Review updated: reviewId={}", reviewId);

        // Re-push updated average to provider-service
        pushAvgRatingToProvider(updated.getProviderId());
        return ReviewResponse.from(updated);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Override
    public void deleteReview(Long reviewId) {
        Review review = findOrThrow(reviewId);
        Long providerId = review.getProviderId();
        reviewRepository.deleteById(reviewId);
        log.info("Review deleted: reviewId={}", reviewId);
        // Recompute provider average after deletion
        pushAvgRatingToProvider(providerId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Review findOrThrow(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));
    }

    /**
     * Calls appointment-service to verify the appointment is COMPLETED.
     * If the call fails, we log and allow the review (non-blocking).
     * In production this would be a hard gate.
     */
    private void verifyAppointmentCompleted(Long appointmentId) {
        try {
            Map<?, ?> appt = restTemplate.getForObject(
                appointmentUrl + "/" + appointmentId, Map.class);
            if (appt != null) {
                String status = (String) appt.get("status");
                if (!"COMPLETED".equals(status)) {
                    throw new ReviewNotAllowedException(
                        "Reviews can only be submitted for COMPLETED appointments. Current status: " + status);
                }
            }
        } catch (ReviewNotAllowedException ex) {
            throw ex; // propagate our own exception
        } catch (Exception ex) {
            log.warn("Could not verify appointment status (non-blocking): {}", ex.getMessage());
        }
    }

    /**
     * Pushes updated avgRating and totalReviews to provider-service.
     * Best-effort — failure here does not affect the review save operation.
     */
    private void pushAvgRatingToProvider(Long providerId) {
        try {
            Double avg = reviewRepository.avgRatingByProviderId(providerId);
            long count = reviewRepository.countByProviderId(providerId);
            Map<String, Object> payload = Map.of(
                "newAvgRating", avg,
                "totalReviews", count
            );
            restTemplate.put(providerUrl + "/" + providerId + "/rating", payload);
            log.debug("Provider avgRating updated: providerId={} avg={} count={}", providerId, avg, count);
        } catch (Exception ex) {
            log.warn("Failed to push avgRating to provider-service (non-blocking): {}", ex.getMessage());
        }
    }
}

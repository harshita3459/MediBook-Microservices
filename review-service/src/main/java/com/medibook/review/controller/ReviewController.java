package com.medibook.review.controller;

import com.medibook.review.dto.*;
import com.medibook.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ReviewResource — REST API for patient reviews.
 * Base URL: /api/v1/reviews   Port: 8086
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reviews", description = "Patient reviews, star ratings and average rating computation")
public class ReviewController {

    private final ReviewService reviewService;

    // ── POST /api/v1/reviews ──────────────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Submit a review after a completed appointment")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ReviewResponse> add(@Valid @RequestBody AddReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.addReview(request));
    }

    // ── GET /api/v1/reviews/appointment/{appointmentId} ───────────────────────
    @GetMapping("/appointment/{appointmentId}")
    @Operation(summary = "Get review for a specific appointment")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ReviewResponse> getByAppointment(@PathVariable Long appointmentId) {
        return ResponseEntity.ok(reviewService.getByAppointment(appointmentId));
    }

    // ── GET /api/v1/reviews/provider/{providerId} ─────────────────────────────
    @GetMapping("/provider/{providerId}")
    @Operation(summary = "Get all reviews for a provider — public facing")
    public ResponseEntity<List<ReviewResponse>> getByProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(reviewService.getByProvider(providerId));
    }

    // ── GET /api/v1/reviews/provider/{providerId}/avg-rating ─────────────────
    @GetMapping("/provider/{providerId}/avg-rating")
    @Operation(summary = "Get average star rating for a provider")
    public ResponseEntity<Map<String, Double>> getAvgRating(@PathVariable Long providerId) {
        return ResponseEntity.ok(Map.of("avgRating", reviewService.getAvgRating(providerId)));
    }

    // ── GET /api/v1/reviews/provider/{providerId}/count ──────────────────────
    @GetMapping("/provider/{providerId}/count")
    @Operation(summary = "Get total number of reviews for a provider")
    public ResponseEntity<Map<String, Long>> getCount(@PathVariable Long providerId) {
        return ResponseEntity.ok(Map.of("count", reviewService.getReviewCount(providerId)));
    }

    // ── GET /api/v1/reviews/patient/{patientId} ───────────────────────────────
    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get all reviews submitted by a patient")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ReviewResponse>> getByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(reviewService.getByPatient(patientId));
    }

    // ── GET /api/v1/reviews (admin) ───────────────────────────────────────────
    @GetMapping
    @Operation(summary = "Get ALL reviews — admin moderation view")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ReviewResponse>> getAll() {
        return ResponseEntity.ok(reviewService.getAllReviews());
    }

    // ── PUT /api/v1/reviews/{id} ──────────────────────────────────────────────
    @PutMapping("/{id}")
    @Operation(summary = "Update a review — patient can edit their own")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ReviewResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReviewRequest request) {
        return ResponseEntity.ok(reviewService.updateReview(id, request));
    }

    // ── DELETE /api/v1/reviews/{id} ───────────────────────────────────────────
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a review — patient or admin")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.ok(Map.of("message", "Review deleted successfully"));
    }
}

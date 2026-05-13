package com.medibook.review.service;

import com.medibook.review.dto.AddReviewRequest;
import com.medibook.review.dto.ReviewResponse;
import com.medibook.review.dto.UpdateReviewRequest;
import com.medibook.review.entity.Review;
import com.medibook.review.exception.ReviewAlreadyExistsException;
import com.medibook.review.exception.ReviewNotAllowedException;
import com.medibook.review.exception.ReviewNotFoundException;
import com.medibook.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for ReviewServiceImpl.
 *
 * Key business rules tested:
 *   - One review per appointment (duplicate check)
 *   - Only COMPLETED appointments can receive reviews
 *   - Anonymous flag is persisted correctly
 *   - Provider average rating is pushed after every add/update/delete
 *   - Update: only non-null fields overwrite existing values
 *   - Delete: triggers rating recalculation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewServiceImpl Tests")
class ReviewServiceImplTest {

    @Mock ReviewRepository reviewRepository;
    @Mock RestTemplate     restTemplate;

    @InjectMocks ReviewServiceImpl reviewService;

    @BeforeEach
    void injectUrls() {
        ReflectionTestUtils.setField(reviewService, "appointmentUrl",
                "http://appointment-svc/api/v1/appointments");
        ReflectionTestUtils.setField(reviewService, "providerUrl",
                "http://provider-svc/api/v1/providers");
    }

    // ── Fixture helpers ───────────────────────────────────────────────────

    private Review buildReview(Long id, Long appointmentId, Long patientId,
                                Long providerId, int rating, String comment) {
        return Review.builder()
                .reviewId(id)
                .appointmentId(appointmentId)
                .patientId(patientId)
                .providerId(providerId)
                .rating(rating)
                .comment(comment)
                .isVerified(false)
                .isAnonymous(false)
                .build();
    }

    private AddReviewRequest buildAddRequest(Long appointmentId, Long patientId,
                                              Long providerId, int rating) {
        return AddReviewRequest.builder()
                .appointmentId(appointmentId)
                .patientId(patientId)
                .providerId(providerId)
                .rating(rating)
                .comment("Good doctor")
                .isAnonymous(false)
                .build();
    }

    /** Stub appointment-service to return COMPLETED status */
    @SuppressWarnings("unchecked")
    private void stubCompletedAppointment(Long appointmentId) {
        given(restTemplate.getForObject(contains("/" + appointmentId), eq(Map.class)))
                .willReturn(Map.of("status", "COMPLETED",
                        "appointmentId", appointmentId));
    }

    /** Stub appointment-service to return a non-COMPLETED status */
    @SuppressWarnings("unchecked")
    private void stubIncompleteAppointment(Long appointmentId, String status) {
        given(restTemplate.getForObject(contains("/" + appointmentId), eq(Map.class)))
                .willReturn(Map.of("status", status,
                        "appointmentId", appointmentId));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // addReview
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("addReview()")
    class AddReviewTests {

        @Test
        @DisplayName("✅ saves review for COMPLETED appointment and pushes rating")
        void add_completedAppointment_savesReview() {
            AddReviewRequest req = buildAddRequest(1L, 1L, 10L, 5);
            given(reviewRepository.existsByAppointmentId(1L)).willReturn(false);
            stubCompletedAppointment(1L);

            Review saved = buildReview(100L, 1L, 1L, 10L, 5, "Great service");
            given(reviewRepository.save(any())).willReturn(saved);

            // avgRating push
            given(reviewRepository.avgRatingByProviderId(10L)).willReturn(4.8);
            given(reviewRepository.countByProviderId(10L)).willReturn(10L);
            willDoNothing().given(restTemplate).put(anyString(), any());

            ReviewResponse result = reviewService.addReview(req);

            assertThat(result.getReviewId()).isEqualTo(100L);
            assertThat(result.getRating()).isEqualTo(5);
            then(reviewRepository).should().save(any());
        }

        @Test
        @DisplayName("✅ anonymous flag is stored on review entity")
        void add_anonymous_persistsFlag() {
            AddReviewRequest req = buildAddRequest(2L, 1L, 10L, 4);
            req.setIsAnonymous(true);
            given(reviewRepository.existsByAppointmentId(2L)).willReturn(false);
            stubCompletedAppointment(2L);

            Review saved = buildReview(101L, 2L, 1L, 10L, 4, null);
            saved.setIsAnonymous(true);
            given(reviewRepository.save(any())).willReturn(saved);

            given(reviewRepository.avgRatingByProviderId(10L)).willReturn(4.0);
            given(reviewRepository.countByProviderId(10L)).willReturn(5L);
            willDoNothing().given(restTemplate).put(anyString(), any());

            ReviewResponse result = reviewService.addReview(req);

            assertThat(result.getIsAnonymous()).isTrue();
            then(reviewRepository).should().save(argThat(r -> Boolean.TRUE.equals(r.getIsAnonymous())));
        }

        @Test
        @DisplayName("❌ duplicate review for same appointmentId → ReviewAlreadyExistsException")
        void add_duplicate_throwsConflict() {
            given(reviewRepository.existsByAppointmentId(3L)).willReturn(true);

            assertThatThrownBy(() ->
                    reviewService.addReview(buildAddRequest(3L, 1L, 10L, 3)))
                    .isInstanceOf(ReviewAlreadyExistsException.class)
                    .hasMessageContaining("3");

            then(reviewRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("❌ SCHEDULED appointment → ReviewNotAllowedException (hard gate)")
        void add_scheduledAppointment_throwsNotAllowed() {
            given(reviewRepository.existsByAppointmentId(4L)).willReturn(false);
            stubIncompleteAppointment(4L, "SCHEDULED");

            assertThatThrownBy(() ->
                    reviewService.addReview(buildAddRequest(4L, 1L, 10L, 5)))
                    .isInstanceOf(ReviewNotAllowedException.class)
                    .hasMessageContaining("COMPLETED")
                    .hasMessageContaining("SCHEDULED");

            then(reviewRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("❌ CANCELLED appointment → ReviewNotAllowedException")
        void add_cancelledAppointment_throwsNotAllowed() {
            given(reviewRepository.existsByAppointmentId(5L)).willReturn(false);
            stubIncompleteAppointment(5L, "CANCELLED");

            assertThatThrownBy(() ->
                    reviewService.addReview(buildAddRequest(5L, 1L, 10L, 2)))
                    .isInstanceOf(ReviewNotAllowedException.class);
        }

        @Test
        @DisplayName("✅ appointment-service down → review allowed (non-blocking fallback)")
        void add_appointmentServiceDown_proceedsAnyway() {
            AddReviewRequest req = buildAddRequest(6L, 1L, 10L, 4);
            given(reviewRepository.existsByAppointmentId(6L)).willReturn(false);

            // Simulate network failure (not a ReviewNotAllowedException)
            given(restTemplate.getForObject(anyString(), eq(Map.class)))
                    .willThrow(new RuntimeException("Connection refused"));

            Review saved = buildReview(102L, 6L, 1L, 10L, 4, "Good");
            given(reviewRepository.save(any())).willReturn(saved);
            given(reviewRepository.avgRatingByProviderId(10L)).willReturn(3.5);
            given(reviewRepository.countByProviderId(10L)).willReturn(3L);
            willDoNothing().given(restTemplate).put(anyString(), any());

            // Should NOT throw — failure is logged and review proceeds
            assertThatCode(() -> reviewService.addReview(req)).doesNotThrowAnyException();
            then(reviewRepository).should().save(any());
        }

        @Test
        @DisplayName("✅ provider average rating is pushed to provider-service after save")
        void add_pushesAvgRatingToProviderService() {
            AddReviewRequest req = buildAddRequest(7L, 1L, 10L, 5);
            given(reviewRepository.existsByAppointmentId(7L)).willReturn(false);
            stubCompletedAppointment(7L);

            Review saved = buildReview(103L, 7L, 1L, 10L, 5, "Excellent");
            given(reviewRepository.save(any())).willReturn(saved);
            given(reviewRepository.avgRatingByProviderId(10L)).willReturn(4.9);
            given(reviewRepository.countByProviderId(10L)).willReturn(20L);

            reviewService.addReview(req);

            // Verify PUT was called to update provider rating
            then(restTemplate).should().put(contains("/10/rating"), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // updateReview
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateReview()")
    class UpdateReviewTests {

        @Test
        @DisplayName("✅ non-null fields overwrite existing, null fields are ignored")
        void update_partialFields_preservesExisting() {
            Review existing = buildReview(1L, 100L, 1L, 10L, 3, "Average");
            given(reviewRepository.findById(1L)).willReturn(Optional.of(existing));
            given(reviewRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(reviewRepository.avgRatingByProviderId(10L)).willReturn(4.0);
            given(reviewRepository.countByProviderId(10L)).willReturn(5L);
            willDoNothing().given(restTemplate).put(anyString(), any());

            UpdateReviewRequest req = UpdateReviewRequest.builder()
                    .rating(5)         // updated
                    .comment(null)     // keep existing "Average"
                    .isAnonymous(true) // updated
                    .build();

            ReviewResponse result = reviewService.updateReview(1L, req);

            assertThat(result.getRating()).isEqualTo(5);
            assertThat(result.getComment()).isEqualTo("Average"); // preserved
            assertThat(result.getIsAnonymous()).isTrue();
        }

        @Test
        @DisplayName("✅ provider rating is re-pushed after update")
        void update_repushesRatingToProvider() {
            Review existing = buildReview(2L, 101L, 1L, 10L, 3, "OK");
            given(reviewRepository.findById(2L)).willReturn(Optional.of(existing));
            given(reviewRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(reviewRepository.avgRatingByProviderId(10L)).willReturn(4.2);
            given(reviewRepository.countByProviderId(10L)).willReturn(8L);

            reviewService.updateReview(2L, UpdateReviewRequest.builder().rating(4).build());

            then(restTemplate).should().put(contains("/10/rating"), any());
        }

        @Test
        @DisplayName("❌ updating non-existent review throws ReviewNotFoundException")
        void update_notFound_throwsException() {
            given(reviewRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    reviewService.updateReview(999L, new UpdateReviewRequest()))
                    .isInstanceOf(ReviewNotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // deleteReview
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteReview()")
    class DeleteReviewTests {

        @Test
        @DisplayName("✅ deletes review and re-pushes updated average rating")
        void delete_existingReview_deletesAndPushesRating() {
            Review review = buildReview(1L, 100L, 1L, 10L, 3, "OK");
            given(reviewRepository.findById(1L)).willReturn(Optional.of(review));
            given(reviewRepository.avgRatingByProviderId(10L)).willReturn(3.5);
            given(reviewRepository.countByProviderId(10L)).willReturn(4L);
            willDoNothing().given(restTemplate).put(anyString(), any());

            reviewService.deleteReview(1L);

            then(reviewRepository).should().deleteById(1L);
            then(restTemplate).should().put(contains("/10/rating"), any());
        }

        @Test
        @DisplayName("❌ deleting non-existent review throws ReviewNotFoundException")
        void delete_notFound_throwsException() {
            given(reviewRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.deleteReview(999L))
                    .isInstanceOf(ReviewNotFoundException.class);

            then(reviewRepository).should(never()).deleteById(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Read operations
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Read operations")
    class ReadTests {

        @Test
        @DisplayName("✅ getByProvider returns reviews sorted newest-first")
        void getByProvider_returnsList() {
            given(reviewRepository.findByProviderIdOrderByReviewDateDesc(10L))
                    .willReturn(List.of(
                            buildReview(1L, 100L, 1L, 10L, 5, "Excellent"),
                            buildReview(2L, 101L, 2L, 10L, 4, "Good")));

            List<ReviewResponse> result = reviewService.getByProvider(10L);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("✅ getByAppointment returns the single review for that appointment")
        void getByAppointment_exists_returnsReview() {
            Review review = buildReview(1L, 100L, 1L, 10L, 5, "Super");
            given(reviewRepository.findByAppointmentId(100L)).willReturn(Optional.of(review));

            ReviewResponse result = reviewService.getByAppointment(100L);

            assertThat(result.getAppointmentId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("❌ getByAppointment for missing appointmentId throws ReviewNotFoundException")
        void getByAppointment_missing_throwsException() {
            given(reviewRepository.findByAppointmentId(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.getByAppointment(999L))
                    .isInstanceOf(ReviewNotFoundException.class);
        }

        @Test
        @DisplayName("✅ getAvgRating delegates to repository")
        void getAvgRating_delegatesToRepo() {
            given(reviewRepository.avgRatingByProviderId(10L)).willReturn(4.3);

            assertThat(reviewService.getAvgRating(10L)).isEqualTo(4.3);
        }

        @Test
        @DisplayName("✅ getReviewCount returns correct count from repository")
        void getReviewCount_returnsCount() {
            given(reviewRepository.countByProviderId(10L)).willReturn(42L);

            assertThat(reviewService.getReviewCount(10L)).isEqualTo(42L);
        }
    }
}
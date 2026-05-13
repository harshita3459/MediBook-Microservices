package com.medibook.review.repository;

import com.medibook.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProviderIdOrderByReviewDateDesc(Long providerId);

    List<Review> findByPatientIdOrderByReviewDateDesc(Long patientId);

    Optional<Review> findByAppointmentId(Long appointmentId);

    boolean existsByAppointmentId(Long appointmentId);

    List<Review> findByRating(Integer rating);

    long countByProviderId(Long providerId);

    /** Average rating for a provider — used to update provider-service */
    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.providerId = :providerId")
    Double avgRatingByProviderId(@Param("providerId") Long providerId);

    /** All reviews sorted newest first — for admin dashboard */
    List<Review> findAllByOrderByReviewDateDesc();

    /** Reviews by rating range — for analytics */
    @Query("SELECT r FROM Review r WHERE r.providerId = :providerId AND r.rating >= :minRating")
    List<Review> findByProviderIdAndMinRating(@Param("providerId") Long providerId,
                                              @Param("minRating") Integer minRating);
}

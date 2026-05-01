package com.medibook.provider.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Provider entity — maps to the 'providers' table in provider_db.
 *
 * IMPORTANT: This entity does NOT store the full User object.
 * It stores only userId (a foreign key by value, not by JPA @ManyToOne)
 * because the User lives in auth_db — a completely different database.
 * Cross-database JPA joins are not possible in microservices architecture.
 * When we need the user's name/email, we call auth-service via HTTP.
 */
@Entity
@Table(
    name = "providers",
    indexes = {
        // Index on userId for fast lookups when auth-service calls us with a userId
        @Index(name = "idx_provider_user_id",       columnList = "user_id"),
        // Index on specialization — most common search filter patients use
        @Index(name = "idx_provider_specialization", columnList = "specialization"),
        // Index on isVerified — admin panel and patient search always filter by this
        @Index(name = "idx_provider_verified",       columnList = "is_verified")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "provider_id")
    private Long providerId;

    /**
     * userId references the User in auth_db.
     * We store it as a plain Long (not a @ManyToOne) because there is no
     * JPA relationship across service boundaries — this is intentional.
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @NotBlank(message = "Specialization is required")
    @Column(name = "specialization", nullable = false, length = 100)
    private String specialization;

    // e.g. "MBBS, MD (Cardiology), DNB"
    @NotBlank(message = "Qualification is required")
    @Column(name = "qualification", nullable = false, length = 300)
    private String qualification;

    // Number of years of practice
    @Min(value = 0, message = "Experience years cannot be negative")
    @Max(value = 60, message = "Experience years seems too high")
    @Column(name = "experience_years")
    @Builder.Default
    private Integer experienceYears = 0;

    // Short biography shown on profile page
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @NotBlank(message = "Clinic name is required")
    @Column(name = "clinic_name", nullable = false, length = 200)
    private String clinicName;

    // Full clinic address — used for location-based search
    @Column(name = "clinic_address", length = 500)
    private String clinicAddress;

    // City extracted separately for faster city-based filtering
    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "clinic_pincode", length = 10)
    private String clinicPincode;

    // Consultation fee in INR
    @DecimalMin(value = "0.0", message = "Fee cannot be negative")
    @Column(name = "consultation_fee")
    @Builder.Default
    private Double consultationFee = 0.0;

    /**
     * avgRating is updated by review-service after every new review.
     * review-service calls PUT /api/v1/providers/{id}/rating with the new average.
     * We don't compute it here — that would require cross-service data.
     */
    @Column(name = "avg_rating")
    @Builder.Default
    private Double avgRating = 0.0;

    // Total number of reviews — stored here for display without calling review-service
    @Column(name = "total_reviews")
    @Builder.Default
    private Integer totalReviews = 0;

    /**
     * isVerified = false until admin reviews and approves credentials.
     * Unverified providers are NOT shown in patient search results.
     */
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean isVerified = false;

    // Provider can temporarily mark themselves unavailable (e.g. on leave)
    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private boolean isAvailable = true;

    // Profile photo URL — stored in S3, just the URL here
    @Column(name = "profile_pic_url", length = 500)
    private String profilePicUrl;

    // Medical registration number — used by admin for verification
    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    // Language spoken — helps patients filter by preferred language
    @Column(name = "languages_spoken", length = 200)
    private String languagesSpoken;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime updatedAt;
}

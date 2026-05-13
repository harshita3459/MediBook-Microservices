package com.medibook.review.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Review — one patient rating+comment per completed appointment.
 * Unique constraint on appointmentId enforces one-review-per-appointment rule.
 */
@Entity
@Table(name = "reviews", indexes = {
    @Index(name = "idx_rev_provider",    columnList = "provider_id"),
    @Index(name = "idx_rev_patient",     columnList = "patient_id"),
    @Index(name = "idx_rev_appointment", columnList = "appointment_id"),
    @Index(name = "idx_rev_rating",      columnList = "rating")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    /** One review per appointment — unique constraint enforced at DB level */
    @NotNull
    @Column(name = "appointment_id", nullable = false, unique = true)
    private Long appointmentId;

    /** References users.user_id in auth_db */
    @NotNull
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** References providers.provider_id in provider_db */
    @NotNull
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /** Star rating: 1 (worst) to 5 (best) */
    @NotNull
    @Min(1) @Max(5)
    @Column(name = "rating", nullable = false)
    private Integer rating;

    /** Written review text — optional */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /**
     * Admin-verified review flag.
     * When false: review visible but flagged for moderation.
     * When true: review cleared by admin.
     */
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    /** Patient can submit review anonymously — name hidden in public display */
    @Column(name = "is_anonymous", nullable = false)
    @Builder.Default
    private Boolean isAnonymous = false;

    @CreationTimestamp
    @Column(name = "review_date", updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime reviewDate;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime updatedAt;
}

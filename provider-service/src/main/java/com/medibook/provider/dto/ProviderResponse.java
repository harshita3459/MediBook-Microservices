package com.medibook.provider.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * What we send BACK to clients in API responses.
 * Notice: no sensitive fields like registrationNumber for public endpoints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderResponse {
 
    private Long   providerId;
    private Long   userId;
    private String specialization;
    private String qualification;
    private int    experienceYears;
    private String bio;
    private String clinicName;
    private String clinicAddress;
    private String city;
    private double consultationFee;
    private double avgRating;
    private int    totalReviews;
    private boolean isVerified;
    private boolean isAvailable;
    private String profilePicUrl;
    private String languagesSpoken;
 
    // These fields are populated by calling auth-service
    // They are null if the auth-service call fails (graceful degradation)
    private String fullName;   // from auth-service
    private String email;      // from auth-service
    private String phone;      // from auth-service
}
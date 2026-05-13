package com.medibook.provider.dto;
 
import jakarta.validation.constraints.*;
import lombok.*;
 
/**
 * DTOs (Data Transfer Objects) — used to send/receive data via REST API.
 *
 *  We control exactly which fields are exposed
 */
 
// ── RegisterProviderRequest ───────────────────────────────────────────────────
 
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RegisterProviderRequest {
 
    // userId comes from the JWT token in the request header —
    // the controller extracts it and passes it here
    @NotNull(message = "User ID is required")
    private Long userId;
 
    @NotBlank(message = "Specialization is required")
    @Size(max = 100, message = "Specialization must be under 100 characters")
    private String specialization;
 
    @NotBlank(message = "Qualification is required")
    @Size(max = 300, message = "Qualification must be under 300 characters")
    private String qualification;
 
    @Min(value = 0, message = "Experience cannot be negative")
    @Max(value = 60, message = "Experience seems unrealistically high")
    private Integer experienceYears;
 
    @Size(max = 2000, message = "Bio must be under 2000 characters")
    private String bio;
 
    @NotBlank(message = "Clinic name is required")
    private String clinicName;
 
    private String clinicAddress;
 
    @NotBlank(message = "City is required")
    private String city;
 
    @DecimalMin(value = "0.0", message = "Fee cannot be negative")
    private Double consultationFee;
 
    private String profilePicUrl;
 
    // Medical council registration number — admin needs this for verification
    private String registrationNumber;
 
    private String languagesSpoken;
}
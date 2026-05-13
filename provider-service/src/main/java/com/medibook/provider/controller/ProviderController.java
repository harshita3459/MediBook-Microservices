package com.medibook.provider.controller;

import com.medibook.provider.entity.Provider;
import com.medibook.provider.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ProviderResource — REST API controller.
 *
 * URL pattern: /api/v1/providers/**
 * Port: 8082
 *
 * Access rules:
 *  - GET  /search, /specialization, /city, /top-rated → public (no token needed)
 *  - POST /register → requires PROVIDER role token
 *  - PUT  /verify, /reject              → requires ADMIN role token
 *  - PUT  /{id}/rating                  → internal call from review-service
 */
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Provider", description = "Healthcare provider profile management, search and verification")
public class ProviderController {

    private final ProviderService providerService;
    private static final String NAME_REGEX = "^(?=.{2,100}$)[A-Za-z][A-Za-z .'-]*$";
    private static final String PINCODE_REGEX = "^[1-9][0-9]{5}$";
    private static final String REGISTRATION_REGEX = "^[A-Z0-9/-]{6,30}$";

    // ── POST /api/v1/providers/register ───────────────────────────────────────
    /**
     * Called when a user with PROVIDER role completes their profile setup.
     * The provider is saved as unverified — admin must approve them.
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new provider profile (requires PROVIDER role)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Provider> registerProvider(
            @Valid @RequestBody RegisterBody body) {

        // Map the request body to a Provider entity
        // We don't use @Mapper libraries here to keep it simple and explicit
        Provider provider = Provider.builder()
                .userId(body.userId())
                .specialization(body.specialization())
                .qualification(body.qualification())
                .experienceYears(body.experienceYears() != null ? body.experienceYears() : 0)
                .bio(body.bio())
                .clinicName(body.clinicName())
                .clinicAddress(body.clinicAddress())
                .city(body.city())
                .clinicPincode(body.clinicPincode())
                .consultationFee(body.consultationFee() != null ? body.consultationFee() : 0.0)
                .profilePicUrl(body.profilePicUrl())
                .registrationNumber(body.registrationNumber())
                .languagesSpoken(body.languagesSpoken())
                .build();

        Provider saved = providerService.registerProvider(provider);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ── GET /api/v1/providers/{id} ────────────────────────────────────────────
    /**
     * Get a single provider by their providerId.
     * Public — guests can view provider profiles without logging in.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get provider profile by ID (public)")
    public ResponseEntity<Provider> getById(@PathVariable Long id) {
        return ResponseEntity.ok(providerService.getProviderById(id));
    }

    // ── GET /api/v1/providers/user/{userId} ───────────────────────────────────
    /**
     * Get provider profile by the userId from auth-service.
     * Called when a provider logs in and the frontend needs their providerId.
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get provider by userId from auth-service")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Provider> getByUserId(@PathVariable Long userId) {
        Provider provider = providerService.getProviderByUserId(userId);
        return ResponseEntity.ok(provider);
    }

    // ── GET /api/v1/providers ─────────────────────────────────────────────────
    /**
     * Browse all verified + available providers.
     * Public — used on the patient browse page.
     */
    @GetMapping
    @Operation(summary = "Get all verified and available providers (public)")
    public ResponseEntity<List<Provider>> getAllVerified() {
        return ResponseEntity.ok(providerService.getAllVerifiedProviders());
    }

    // ── GET /api/v1/providers/search?keyword=cardio ───────────────────────────
    /**
     * Full-text search across specialization, clinic name, city.
     * Public — used in the search bar on the homepage.
     */
    @GetMapping("/search")
    @Operation(summary = "Search providers by keyword (public)")
    public ResponseEntity<List<Provider>> search(
            @RequestParam @NotBlank String keyword) {
        return ResponseEntity.ok(providerService.searchProviders(keyword));
    }

    // ── GET /api/v1/providers/specialization/{spec} ───────────────────────────
    @GetMapping("/specialization/{spec}")
    @Operation(summary = "Get providers by specialization (public)")
    public ResponseEntity<List<Provider>> getBySpecialization(
            @PathVariable String spec) {
        return ResponseEntity.ok(providerService.getBySpecialization(spec));
    }

    // ── GET /api/v1/providers/city/{city} ─────────────────────────────────────
    @GetMapping("/city/{city}")
    @Operation(summary = "Get providers by city (public)")
    public ResponseEntity<List<Provider>> getByCity(@PathVariable String city) {
        return ResponseEntity.ok(providerService.getByCity(city));
    }

    // ── GET /api/v1/providers/top-rated?minRating=4.0 ────────────────────────
    @GetMapping("/top-rated")
    @Operation(summary = "Get top-rated providers (public)")
    public ResponseEntity<List<Provider>> getTopRated(
            @RequestParam(defaultValue = "4.0") double minRating) {
        return ResponseEntity.ok(providerService.getTopRated(minRating));
    }

    // ── GET /api/v1/providers/fee-range?min=200&max=500 ───────────────────────
    @GetMapping("/fee-range")
    @Operation(summary = "Get providers within a fee range (public)")
    public ResponseEntity<List<Provider>> getByFeeRange(
            @RequestParam(defaultValue = "0")    double min,
            @RequestParam(defaultValue = "10000") double max) {
        return ResponseEntity.ok(providerService.getByFeeRange(min, max));
    }

    // ── PUT /api/v1/providers/{id} ────────────────────────────────────────────
    @PutMapping("/{id}")
    @Operation(summary = "Update provider profile (PROVIDER role)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Provider> updateProvider(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBody body) {

        Provider updates = Provider.builder()
                .specialization(body.specialization())
                .qualification(body.qualification())
                .experienceYears(body.experienceYears())
                .bio(body.bio())
                .clinicName(body.clinicName())
                .clinicAddress(body.clinicAddress())
                .city(body.city())
                .clinicPincode(body.clinicPincode())
                .consultationFee(body.consultationFee())
                .profilePicUrl(body.profilePicUrl())
                .registrationNumber(body.registrationNumber())
                .languagesSpoken(body.languagesSpoken())
                .build();

        return ResponseEntity.ok(providerService.updateProvider(id, updates));
    }

    // ── PUT /api/v1/providers/{id}/verify ─────────────────────────────────────
    /**
     * Admin approves a provider — makes them visible to patients.
     * Only ADMIN role should call this endpoint.
     */
    @PutMapping("/{id}/verify")
    @Operation(summary = "Verify provider credentials (ADMIN only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Provider> verify(@PathVariable Long id) {
        return ResponseEntity.ok(providerService.verifyProvider(id));
    }

    // ── PUT /api/v1/providers/{id}/reject ─────────────────────────────────────
    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject provider credentials (ADMIN only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Provider> reject(@PathVariable Long id) {
        return ResponseEntity.ok(providerService.rejectProvider(id));
    }

    // ── GET /api/v1/providers/admin/pending ───────────────────────────────────
    @GetMapping("/admin/pending")
    @Operation(summary = "Get providers pending verification (ADMIN only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<Provider>> getPending() {
        return ResponseEntity.ok(providerService.getPendingVerification());
    }

    // ── GET /api/v1/providers/admin/all ───────────────────────────────────────
    @GetMapping("/admin/all")
    @Operation(summary = "Get ALL providers including unverified (ADMIN only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<Provider>> getAll() {
        return ResponseEntity.ok(providerService.getAllProviders());
    }

    // ── PUT /api/v1/providers/{id}/availability ───────────────────────────────
    @PutMapping("/{id}/availability")
    @Operation(summary = "Toggle provider availability (PROVIDER role)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Provider> setAvailability(
            @PathVariable Long id,
            @RequestParam boolean available) {
        return ResponseEntity.ok(providerService.setAvailability(id, available));
    }

    // ── PUT /api/v1/providers/{id}/rating ─────────────────────────────────────
    /**
     * Internal endpoint — called ONLY by review-service after a new review.
     * Updates avgRating and totalReviews on the Provider record.
     * In production, secure this with an internal API key or service-to-service JWT.
     */
    @PutMapping("/{id}/rating")
    @Operation(summary = "Update provider rating (internal — called by review-service)")
    public ResponseEntity<Provider> updateRating(
            @PathVariable Long id,
            @RequestBody RatingBody body) {
        return ResponseEntity.ok(
            providerService.updateRating(id, body.newAvgRating(), body.totalReviews()));
    }

    // ── DELETE /api/v1/providers/{id} ─────────────────────────────────────────
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete provider profile (ADMIN only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        providerService.deleteProvider(id);
        return ResponseEntity.ok(Map.of("message", "Provider deleted successfully"));
    }

    // ── Request body records ──────────────────────────────────────────────────
    // Java 17 records — immutable, compact DTOs for incoming request bodies

    record RegisterBody(
        @NotNull Long   userId,
        @NotBlank @Size(max = 100) String specialization,
        @NotBlank @Size(max = 300) String qualification,
        @Min(0) @Max(60) Integer experienceYears,
        @Size(max = 2000) String  bio,
        @NotBlank @Size(max = 200) @Pattern(regexp = NAME_REGEX, message = "Clinic name can contain only letters and basic punctuation") String clinicName,
        @Size(max = 500) String  clinicAddress,
        @NotBlank @Size(max = 100) @Pattern(regexp = NAME_REGEX, message = "City can contain only letters and basic punctuation") String city,
        @Pattern(regexp = PINCODE_REGEX, message = "Pincode must be a valid 6-digit Indian pincode") String clinicPincode,
        @DecimalMin("0.0") Double  consultationFee,
        @Size(max = 500) String  profilePicUrl,
        @Pattern(regexp = REGISTRATION_REGEX, message = "Registration number must be 6-30 characters using letters, digits, / or -") String  registrationNumber,
        @Size(max = 200) String  languagesSpoken
    ) {}

    record UpdateBody(
        @Size(max = 100) String  specialization,
        @Size(max = 300) String  qualification,
        @Min(0) @Max(60) Integer experienceYears,
        @Size(max = 2000) String  bio,
        @Pattern(regexp = NAME_REGEX, message = "Clinic name can contain only letters and basic punctuation") String  clinicName,
        @Size(max = 500) String  clinicAddress,
        @Pattern(regexp = NAME_REGEX, message = "City can contain only letters and basic punctuation") String  city,
        @Pattern(regexp = PINCODE_REGEX, message = "Pincode must be a valid 6-digit Indian pincode") String clinicPincode,
        @DecimalMin("0.0") Double  consultationFee,
        @Size(max = 500) String  profilePicUrl,
        @Pattern(regexp = REGISTRATION_REGEX, message = "Registration number must be 6-30 characters using letters, digits, / or -") String  registrationNumber,
        @Size(max = 200) String  languagesSpoken
    ) {}

    record RatingBody(
        @DecimalMin("0.0") @DecimalMax("5.0") double newAvgRating,
        @Min(0) int totalReviews
    ) {}
}

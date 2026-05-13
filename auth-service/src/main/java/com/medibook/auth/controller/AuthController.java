package com.medibook.auth.controller;
import com.medibook.auth.entity.User;
import com.medibook.auth.exception.UserAlreadyExistsException;
import com.medibook.auth.service.AuthService;
import com.medibook.auth.util.JwtUtil;
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

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User registration, login, and profile management")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private static final String NAME_REGEX = "^(?=.{2,100}$)[A-Za-z][A-Za-z .'-]*$";
    private static final String PHONE_REGEX = "^[6-9]\\d{9}$";
    private static final String PASSWORD_REGEX = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*#?&]).{8,64}$";

    // ── POST /api/v1/auth/register ─────────────────────────────────────────────
    @PostMapping("/register")
    @Operation(summary = "Register a new patient or provider account")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterBody body) throws UserAlreadyExistsException {
        User user = authService.register(
            body.fullName(), body.email(),
            body.password(), body.phone(), body.role());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message", "Registration successful",
            "userId",  user.getUserId(),
            "email",   user.getEmail(),
            "role",    user.getRole().name()
        ));
    }

    // ── POST /api/v1/auth/login ────────────────────────────────────────────────
    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public ResponseEntity<?> login(@Valid @RequestBody LoginBody body) {
        Map<String, Object> response = authService.login(body.email(), body.password());
        return ResponseEntity.ok(response);
    }

    // ── POST /api/v1/auth/logout ───────────────────────────────────────────────
    @PostMapping("/logout")
    @Operation(summary = "Logout — invalidate the current token")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> logout(
            @RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        authService.logout(token);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // ── POST /api/v1/auth/refresh ──────────────────────────────────────────────
    @PostMapping("/refresh")
    @Operation(summary = "Get a new access token using refresh token")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String newToken = authService.refreshToken(body.get("refreshToken"));
        return ResponseEntity.ok(Map.of(
            "accessToken", newToken,
            "tokenType",   "Bearer",
            "expiresIn",   jwtUtil.getExpiration()
        ));
    }

    // ── GET /api/v1/auth/validate ──────────────────────────────────────────────
    @GetMapping("/validate")
    @Operation(summary = "Validate a JWT token — used by other microservices")
    public ResponseEntity<?> validate(
            @RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        boolean valid = authService.validateToken(token);

        if (!valid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "message", "Token is invalid or expired"));
        }

        return ResponseEntity.ok(Map.of(
            "valid",  true,
            "email",  jwtUtil.extractEmail(token),
            "role",   jwtUtil.extractRole(token),
            "userId", jwtUtil.extractUserId(token)
        ));
    }

    // ── GET /api/v1/auth/profile/{userId} ─────────────────────────────────────
    @GetMapping("/profile/{userId}")
    @Operation(summary = "Get user profile by ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> getProfile(@PathVariable Long userId) {
        User user = authService.getUserById(userId);
        return ResponseEntity.ok(toProfileResponse(user));
    }

    // ── PUT /api/v1/auth/profile/{userId} ─────────────────────────────────────
    @PutMapping("/profile/{userId}")
    @Operation(summary = "Update user profile")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> updateProfile(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateProfileBody body) {
        User updated = authService.updateProfile(
            userId, body.fullName(), body.phone(), body.profilePicUrl());
        return ResponseEntity.ok(toProfileResponse(updated));
    }

    // ── PUT /api/v1/auth/password/{userId} ────────────────────────────────────
    @PutMapping("/password/{userId}")
    @Operation(summary = "Change user password")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> changePassword(
            @PathVariable Long userId,
            @Valid @RequestBody ChangePasswordBody body) {
        authService.changePassword(userId, body.currentPassword(), body.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    // ── DELETE /api/v1/auth/deactivate/{userId} ────────────────────────────────
    @DeleteMapping("/deactivate/{userId}")
    @Operation(summary = "Deactivate user account")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> deactivate(@PathVariable Long userId) {
        authService.deactivateAccount(userId);
        return ResponseEntity.ok(Map.of("message", "Account deactivated successfully"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid Authorization header format");
    }

    private Map<String, Object> toProfileResponse(User user) {
        return Map.of(
            "userId",        user.getUserId(),
            "fullName",      user.getFullName(),
            "email",         user.getEmail(),
            "phone",         user.getPhone() != null ? user.getPhone() : "",
            "role",          user.getRole().name(),
            "provider",      user.getProvider().name(),
            "isActive",      user.isActive(),
            "profilePicUrl", user.getProfilePicUrl() != null ? user.getProfilePicUrl() : "",
            "createdAt",     user.getCreatedAt()
        );
    }

    // ── Request body records (Java 17 records as inline DTOs) ─────────────────

    record RegisterBody(
        @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = NAME_REGEX, message = "Full name can contain only letters and basic punctuation") String fullName,
        @Email @NotBlank String email,
        @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = "Password must be 8-64 characters and include upper, lower, number and special character") String password,
        @Pattern(regexp = "^$|" + PHONE_REGEX, message = "Phone number must be a valid 10-digit Indian mobile number") String phone,
        @NotNull User.Role role
    ) {}

    record LoginBody(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 64) String password
    ) {}

    record UpdateProfileBody(
        @Pattern(regexp = "^$|" + NAME_REGEX, message = "Full name can contain only letters and basic punctuation") String fullName,
        @Pattern(regexp = "^$|" + PHONE_REGEX, message = "Phone number must be a valid 10-digit Indian mobile number") String phone,
        @Size(max = 500) String profilePicUrl
    ) {}

    record ChangePasswordBody(
        @NotBlank String currentPassword,
        @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = "Password must be 8-64 characters and include upper, lower, number and special character") String newPassword
    ) {}
}

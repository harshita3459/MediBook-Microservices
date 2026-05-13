package com.medibook.auth.service;

import com.medibook.auth.entity.User;
import com.medibook.auth.exception.InvalidCredentialsException;
import com.medibook.auth.exception.UserAlreadyExistsException;
import com.medibook.auth.repository.UserRepository;
import com.medibook.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // ── Register ───────────────────────────────────────────────────────────────

    @Override
    public User register(String fullName, String email,
                         String password, String phone, User.Role role) throws UserAlreadyExistsException {
        log.info("Registering new user with email: {}", email);

        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(
                "User already exists with email: " + email);
        }

        User user = User.builder()
                .fullName(fullName)
                .email(email.toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(password))
                .phone(phone)
                .role(role)
                .provider(User.AuthProvider.LOCAL)
                .isActive(true)
                .build();

        User saved = userRepository.save(user);
        log.info("User registered successfully with ID: {}", saved.getUserId());
        return saved;
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> login(String email, String password) {
        log.info("Login attempt for email: {}", email);

        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new InvalidCredentialsException(
                    "Invalid email or password"));

        if (!user.isActive()) {
            throw new InvalidCredentialsException(
                "Account is deactivated. Please contact support.");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String accessToken  = jwtUtil.generateAccessToken(
            user.getEmail(), user.getRole().name(), user.getUserId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken",  accessToken);
        response.put("refreshToken", refreshToken);
        response.put("tokenType",    "Bearer");
        response.put("expiresIn",    jwtUtil.getExpiration());
        response.put("userId",       user.getUserId());
        response.put("fullName",     user.getFullName());
        response.put("email",        user.getEmail());
        response.put("role",         user.getRole().name());
        response.put("profilePicUrl",user.getProfilePicUrl());

        log.info("Login successful for user: {}", email);
        return response;
    }

    // ── Logout ─────────────────────────────────────────────────────────────────

    @Override
    public void logout(String token) {
        // In a production system, add the token to a Redis blacklist here.
        // For now, logout is handled client-side by discarding the token.
        log.info("Logout called — token should be discarded by client");
    }

    // ── Token Validation ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            return false;
        }
        String email = jwtUtil.extractEmail(token);
        return userRepository.findByEmail(email)
                .map(User::isActive)
                .orElse(false);
    }

    // ── Refresh Token ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public String refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
        String email = jwtUtil.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        return jwtUtil.generateAccessToken(
            user.getEmail(), user.getRole().name(), user.getUserId());
    }

    // ── Get User ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                    "User not found with email: " + email));
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(
                    "User not found with ID: " + userId));
    }

    // ── Update Profile ─────────────────────────────────────────────────────────

    @Override
    public User updateProfile(Long userId, String fullName,
                               String phone, String profilePicUrl) {
        User user = getUserById(userId);

        if (fullName     != null && !fullName.isBlank())     user.setFullName(fullName);
        if (phone        != null && !phone.isBlank())         user.setPhone(phone);
        if (profilePicUrl != null && !profilePicUrl.isBlank()) user.setProfilePicUrl(profilePicUrl);

        User updated = userRepository.save(user);
        log.info("Profile updated for user: {}", userId);
        return updated;
    }

    // ── Change Password ────────────────────────────────────────────────────────

    @Override
    public void changePassword(Long userId,
                                String currentPassword, String newPassword) {
        User user = getUserById(userId);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user: {}", userId);
    }

    // ── Deactivate Account ─────────────────────────────────────────────────────

    @Override
    public void deactivateAccount(Long userId) {
        User user = getUserById(userId);
        user.setActive(false);
        userRepository.save(user);
        log.info("Account deactivated for user: {}", userId);
    }

    // ── OAuth2 User Processing ─────────────────────────────────────────────────

    @Override
    public User processOAuthUser(String email, String fullName,
                                  String providerId, User.AuthProvider provider) {
        return userRepository.findByEmail(email)
                .map(existingUser -> {
                    // Update OAuth info if user already exists
                    existingUser.setProviderId(providerId);
                    existingUser.setProvider(provider);
                    if (existingUser.getFullName() == null) {
                        existingUser.setFullName(fullName);
                    }
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    // Create new user from OAuth
                    User newUser = User.builder()
                            .email(email.toLowerCase().trim())
                            .fullName(fullName)
                            .provider(provider)
                            .providerId(providerId)
                            .role(User.Role.PATIENT)   // OAuth users default to PATIENT
                            .isActive(true)
                            .build();
                    return userRepository.save(newUser);
                });
    }
}

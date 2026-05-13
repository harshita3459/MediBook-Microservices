package com.medibook.auth.service;

import com.medibook.auth.entity.User;
import com.medibook.auth.exception.InvalidCredentialsException;
import com.medibook.auth.exception.UserAlreadyExistsException;
import com.medibook.auth.repository.UserRepository;
import com.medibook.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for AuthServiceImpl.
 * All collaborators are mocked — no Spring context, no DB, blazing fast.
 *
 * Pattern: Arrange → Act → Assert (AAA)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl Tests")
class AuthServiceImplTest {

    @Mock UserRepository    userRepository;
    @Mock PasswordEncoder   passwordEncoder;
    @Mock JwtUtil           jwtUtil;

    @InjectMocks
    AuthServiceImpl authService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private User buildUser(Long id, String email, boolean active) {
        return User.builder()
                .userId(id)
                .fullName("Test User")
                .email(email)
                .passwordHash("$2a$12$hashedpw")
                .role(User.Role.PATIENT)
                .provider(User.AuthProvider.LOCAL)
                .isActive(active)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REGISTER
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("✅ registers new user successfully and returns saved entity")
        void register_newUser_returnsSavedUser() throws UserAlreadyExistsException {
            // Arrange
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            User saved = buildUser(1L, "patient@test.com", true);
            given(userRepository.save(any(User.class))).willReturn(saved);
            given(passwordEncoder.encode(anyString())).willReturn("$2a$12$hash");

            // Act
            User result = authService.register(
                    "Test User", "patient@test.com",
                    "Pass@123", "9876543210", User.Role.PATIENT);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getEmail()).isEqualTo("patient@test.com");
            then(userRepository).should().save(any(User.class));
        }

        @Test
        @DisplayName("✅ email is stored lowercase-trimmed")
        void register_emailNormalized_toLowercase() throws UserAlreadyExistsException {
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            User saved = buildUser(1L, "doctor@test.com", true);
            given(userRepository.save(any(User.class))).willReturn(saved);
            given(passwordEncoder.encode(anyString())).willReturn("hash");

            authService.register("Dr Test", "  Doctor@Test.COM  ",
                    "Pass@1234", null, User.Role.PROVIDER);

            then(userRepository).should().save(argThat(u ->
                    u.getEmail().equals("doctor@test.com")
            ));
        }

        @Test
        @DisplayName("❌ throws UserAlreadyExistsException when email taken")
        void register_duplicateEmail_throwsException() {
            given(userRepository.existsByEmail("dup@test.com")).willReturn(true);

            assertThatThrownBy(() ->
                    authService.register("Dup User", "dup@test.com",
                            "Pass@1234", null, User.Role.PATIENT))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("dup@test.com");

            then(userRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("✅ password is encoded before saving — never stored in plaintext")
        void register_passwordIsEncoded() throws UserAlreadyExistsException {
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(passwordEncoder.encode("MyPl@inPw")).willReturn("$bcrypt$hash");
            User saved = buildUser(1L, "enc@test.com", true);
            given(userRepository.save(any(User.class))).willReturn(saved);

            authService.register("Enc User", "enc@test.com",
                    "MyPl@inPw", null, User.Role.PATIENT);

            then(userRepository).should().save(argThat(u ->
                    "$bcrypt$hash".equals(u.getPasswordHash())
            ));
        }

        @Test
        @DisplayName("✅ new users start as isActive=true")
        void register_newUser_isActiveByDefault() throws UserAlreadyExistsException {
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            User saved = buildUser(1L, "new@test.com", true);
            given(userRepository.save(any(User.class))).willReturn(saved);
            given(passwordEncoder.encode(anyString())).willReturn("hash");

            authService.register("New", "new@test.com", "Pass@123", null, User.Role.PATIENT);

            then(userRepository).should().save(argThat(User::isActive));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOGIN
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("✅ successful login returns all expected token fields")
        void login_validCredentials_returnsTokenMap() {
            // Arrange
            User user = buildUser(1L, "patient@test.com", true);
            given(userRepository.findByEmail("patient@test.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Pass@123", user.getPasswordHash())).willReturn(true);
            given(jwtUtil.generateAccessToken(anyString(), anyString(), anyLong()))
                    .willReturn("access.jwt.token");
            given(jwtUtil.generateRefreshToken(anyString())).willReturn("refresh.jwt.token");
            given(jwtUtil.getExpiration()).willReturn(3600000L);

            // Act
            Map<String, Object> result = authService.login("patient@test.com", "Pass@123");

            // Assert
            assertThat(result)
                    .containsKey("accessToken")
                    .containsKey("refreshToken")
                    .containsEntry("tokenType", "Bearer")
                    .containsEntry("userId",    1L)
                    .containsEntry("email",     "patient@test.com")
                    .containsEntry("role",      "PATIENT");
            assertThat(result.get("accessToken")).isEqualTo("access.jwt.token");
        }

        @Test
        @DisplayName("❌ non-existent email throws InvalidCredentialsException")
        void login_emailNotFound_throwsInvalidCredentials() {
            given(userRepository.findByEmail("ghost@test.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login("ghost@test.com", "anyPw"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("❌ wrong password throws InvalidCredentialsException")
        void login_wrongPassword_throwsInvalidCredentials() {
            User user = buildUser(1L, "real@test.com", true);
            given(userRepository.findByEmail("real@test.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("WrongPw", user.getPasswordHash())).willReturn(false);

            assertThatThrownBy(() -> authService.login("real@test.com", "WrongPw"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("❌ deactivated account throws InvalidCredentialsException")
        void login_deactivatedAccount_throwsException() {
            User user = buildUser(1L, "banned@test.com", false); // isActive = false
            given(userRepository.findByEmail("banned@test.com")).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login("banned@test.com", "Pass@123"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("deactivated");

            then(passwordEncoder).should(never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("✅ email lookup is case-insensitive (lowercase normalize)")
        void login_uppercaseEmail_normalizedToLower() {
            User user = buildUser(1L, "case@test.com", true);
            given(userRepository.findByEmail("case@test.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
            given(jwtUtil.generateAccessToken(anyString(), anyString(), anyLong())).willReturn("tok");
            given(jwtUtil.generateRefreshToken(anyString())).willReturn("ref");
            given(jwtUtil.getExpiration()).willReturn(1000L);

            // Should normalise "CASE@TEST.COM" → "case@test.com" before DB lookup
            assertThatCode(() -> authService.login("CASE@TEST.COM", "Pass@123"))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VALIDATE TOKEN
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("validateToken()")
    class ValidateTokenTests {

        @Test
        @DisplayName("✅ valid token for active user returns true")
        void validateToken_validActiveUser_returnsTrue() {
            User user = buildUser(1L, "active@test.com", true);
            given(jwtUtil.validateToken("valid.token")).willReturn(true);
            given(jwtUtil.extractEmail("valid.token")).willReturn("active@test.com");
            given(userRepository.findByEmail("active@test.com")).willReturn(Optional.of(user));

            assertThat(authService.validateToken("valid.token")).isTrue();
        }

        @Test
        @DisplayName("❌ invalid JWT signature returns false")
        void validateToken_invalidJwt_returnsFalse() {
            given(jwtUtil.validateToken("bad.token")).willReturn(false);

            assertThat(authService.validateToken("bad.token")).isFalse();
            then(userRepository).should(never()).findByEmail(anyString());
        }

        @Test
        @DisplayName("❌ valid JWT but user deactivated returns false")
        void validateToken_deactivatedUser_returnsFalse() {
            User user = buildUser(1L, "off@test.com", false);
            given(jwtUtil.validateToken("tok")).willReturn(true);
            given(jwtUtil.extractEmail("tok")).willReturn("off@test.com");
            given(userRepository.findByEmail("off@test.com")).willReturn(Optional.of(user));

            assertThat(authService.validateToken("tok")).isFalse();
        }

        @Test
        @DisplayName("❌ valid JWT but user deleted (not found) returns false")
        void validateToken_userDeleted_returnsFalse() {
            given(jwtUtil.validateToken("tok")).willReturn(true);
            given(jwtUtil.extractEmail("tok")).willReturn("gone@test.com");
            given(userRepository.findByEmail("gone@test.com")).willReturn(Optional.empty());

            assertThat(authService.validateToken("tok")).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REFRESH TOKEN
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("refreshToken()")
    class RefreshTokenTests {

        @Test
        @DisplayName("✅ valid refresh token generates new access token")
        void refreshToken_valid_returnsNewAccessToken() {
            User user = buildUser(1L, "user@test.com", true);
            given(jwtUtil.validateToken("refreshTok")).willReturn(true);
            given(jwtUtil.extractEmail("refreshTok")).willReturn("user@test.com");
            given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
            given(jwtUtil.generateAccessToken("user@test.com", "PATIENT", 1L))
                    .willReturn("newAccessTok");

            String result = authService.refreshToken("refreshTok");

            assertThat(result).isEqualTo("newAccessTok");
        }

        @Test
        @DisplayName("❌ expired refresh token throws InvalidCredentialsException")
        void refreshToken_expired_throwsException() {
            given(jwtUtil.validateToken("expired")).willReturn(false);

            assertThatThrownBy(() -> authService.refreshToken("expired"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("Invalid or expired refresh token");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UPDATE PROFILE
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfileTests {

        @Test
        @DisplayName("✅ non-null fields are updated, nulls are ignored")
        void updateProfile_partialUpdate_onlyNonNullFieldsChanged() {
            User user = buildUser(1L, "upd@test.com", true);
            user.setPhone("9000000000");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            User result = authService.updateProfile(1L, "New Name", null, "http://pic.url");

            assertThat(result.getFullName()).isEqualTo("New Name");
            assertThat(result.getPhone()).isEqualTo("9000000000"); // unchanged
            assertThat(result.getProfilePicUrl()).isEqualTo("http://pic.url");
        }

        @Test
        @DisplayName("❌ updating non-existent user throws RuntimeException")
        void updateProfile_userNotFound_throwsException() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.updateProfile(999L, "Name", null, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CHANGE PASSWORD
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test
        @DisplayName("✅ correct current password updates hash")
        void changePassword_correctCurrent_updatesHash() {
            User user = buildUser(1L, "pw@test.com", true);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("OldPw", user.getPasswordHash())).willReturn(true);
            given(passwordEncoder.encode("NewPw@1")).willReturn("$newHash");
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> authService.changePassword(1L, "OldPw", "NewPw@1"))
                    .doesNotThrowAnyException();

            then(userRepository).should().save(argThat(u -> "$newHash".equals(u.getPasswordHash())));
        }

        @Test
        @DisplayName("❌ wrong current password throws InvalidCredentialsException")
        void changePassword_wrongCurrent_throwsException() {
            User user = buildUser(1L, "pw@test.com", true);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("WrongOldPw", user.getPasswordHash())).willReturn(false);

            assertThatThrownBy(() -> authService.changePassword(1L, "WrongOldPw", "NewPw@1"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("Current password is incorrect");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DEACTIVATE ACCOUNT
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deactivateAccount()")
    class DeactivateTests {

        @Test
        @DisplayName("✅ active user is set to isActive=false")
        void deactivate_activeUser_setsInactive() {
            User user = buildUser(1L, "active@test.com", true);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            authService.deactivateAccount(1L);

            then(userRepository).should().save(argThat(u -> !u.isActive()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OAUTH2 USER PROCESSING
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processOAuthUser()")
    class OAuthTests {

        @Test
        @DisplayName("✅ new OAuth user is created with PATIENT role")
        void processOAuth_newUser_createdAsPatient() {
            given(userRepository.findByEmail("oauth@google.com")).willReturn(Optional.empty());
            User saved = buildUser(2L, "oauth@google.com", true);
            saved.setProvider(User.AuthProvider.GOOGLE);
            given(userRepository.save(any(User.class))).willReturn(saved);

            User result = authService.processOAuthUser(
                    "oauth@google.com", "Google User", "g123", User.AuthProvider.GOOGLE);

            assertThat(result.getProvider()).isEqualTo(User.AuthProvider.GOOGLE);
            then(userRepository).should().save(argThat(u ->
                    u.getRole() == User.Role.PATIENT
                    && u.getProvider() == User.AuthProvider.GOOGLE
            ));
        }

        @Test
        @DisplayName("✅ existing user's OAuth provider info is updated on re-login")
        void processOAuth_existingUser_updatesProvider() {
            User existing = buildUser(3L, "exist@test.com", true);
            existing.setProvider(User.AuthProvider.LOCAL);
            given(userRepository.findByEmail("exist@test.com")).willReturn(Optional.of(existing));
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            authService.processOAuthUser(
                    "exist@test.com", "Same User", "p456", User.AuthProvider.GOOGLE);

            then(userRepository).should().save(argThat(u ->
                    "p456".equals(u.getProviderId())
                    && u.getProvider() == User.AuthProvider.GOOGLE
            ));
        }
    }
}
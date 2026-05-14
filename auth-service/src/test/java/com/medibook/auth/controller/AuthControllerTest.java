package com.medibook.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medibook.auth.entity.User;
import com.medibook.auth.service.AuthService;
import com.medibook.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private User buildUser() {
        return User.builder()
                .userId(1L)
                .fullName("John Doe")
                .email("john@example.com")
                .phone("9876543210")
                .role(User.Role.PATIENT)
                .provider(User.AuthProvider.LOCAL)
                .isActive(true)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register -> 201 CREATED")
    void register_validRequest_returns201() throws Exception {
        given(authService.register(anyString(), anyString(), anyString(), anyString(), any()))
                .willReturn(buildUser());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"John Doe\",\"email\":\"john@example.com\"," +
                                "\"password\":\"Password@1\",\"phone\":\"9876543210\",\"role\":\"PATIENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login -> 200 OK with tokens")
    void login_validCredentials_returns200() throws Exception {
        Map<String, Object> loginResp = Map.of(
                "accessToken", "eyJhbGci.token.here",
                "refreshToken", "refresh.token.here",
                "role", "PATIENT",
                "userId", 1L
        );
        given(authService.login(anyString(), anyString())).willReturn(loginResp);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"john@example.com\",\"password\":\"Password@1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout -> 200 OK")
    void logout_validToken_returns200() throws Exception {
        willDoNothing().given(authService).logout(anyString());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer somevalidtoken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh -> 200 OK with new access token")
    void refresh_validRefreshToken_returns200() throws Exception {
        given(authService.refreshToken(anyString())).willReturn("new.access.token");
        given(jwtUtil.getExpiration()).willReturn(86400000L);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh.token.here\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/validate -> 200 OK when token is valid")
    void validate_validToken_returns200WithDetails() throws Exception {
        given(authService.validateToken(anyString())).willReturn(true);
        given(jwtUtil.extractEmail(anyString())).willReturn("john@example.com");
        given(jwtUtil.extractRole(anyString())).willReturn("PATIENT");
        given(jwtUtil.extractUserId(anyString())).willReturn(1L);

        mockMvc.perform(get("/api/v1/auth/validate")
                        .header("Authorization", "Bearer somevalidtoken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/validate -> 401 when token is invalid")
    void validate_invalidToken_returns401() throws Exception {
        given(authService.validateToken(anyString())).willReturn(false);

        mockMvc.perform(get("/api/v1/auth/validate")
                        .header("Authorization", "Bearer invalidtoken"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/auth/profile/{userId} -> 200 OK")
    void getProfile_returns200() throws Exception {
        given(authService.getUserById(1L)).willReturn(buildUser());

        mockMvc.perform(get("/api/v1/auth/profile/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    @DisplayName("PUT /api/v1/auth/profile/{userId} -> 200 OK with updated profile")
    void updateProfile_returns200() throws Exception {
        User updated = buildUser();
        updated.setFullName("John Updated");
        given(authService.updateProfile(eq(1L), anyString(), anyString(), any())).willReturn(updated);

        mockMvc.perform(put("/api/v1/auth/profile/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"John Updated\",\"phone\":\"9876543210\",\"profilePicUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("John Updated"));
    }

    @Test
    @DisplayName("PUT /api/v1/auth/password/{userId} -> 200 OK")
    void changePassword_returns200() throws Exception {
        willDoNothing().given(authService).changePassword(eq(1L), anyString(), anyString());

        mockMvc.perform(put("/api/v1/auth/password/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldPass@1\",\"newPassword\":\"NewPass@2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/deactivate/{userId} -> 200 OK")
    void deactivate_returns200() throws Exception {
        willDoNothing().given(authService).deactivateAccount(1L);

        mockMvc.perform(delete("/api/v1/auth/deactivate/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account deactivated successfully"));
    }
}

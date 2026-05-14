package com.medibook.provider.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medibook.provider.entity.Provider;
import com.medibook.provider.service.ProviderService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderController Tests")
class ProviderControllerTest {

    @Mock
    private ProviderService providerService;

    @InjectMocks
    private ProviderController providerController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(providerController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private Provider buildProvider(Long id) {
        return Provider.builder()
                .providerId(id)
                .userId(10L)
                .specialization("Cardiology")
                .qualification("MBBS, MD")
                .experienceYears(8)
                .clinicName("Heart Care Clinic")
                .clinicAddress("123 Main St")
                .city("Mumbai")
                .consultationFee(800.0)
                .avgRating(4.5)
                .totalReviews(30)
                .isVerified(true)
                .isAvailable(true)
                .build();
    }

    private String registerBodyJson() {
        return """
                {
                  "userId": 10,
                  "specialization": "Cardiology",
                  "qualification": "MBBS, MD",
                  "experienceYears": 8,
                  "clinicName": "Heart Care Clinic",
                  "city": "Mumbai",
                  "consultationFee": 800.0
                }
                """;
    }

    @Test
    @DisplayName("POST /api/v1/providers/register -> 201 CREATED")
    void register_validRequest_returns201() throws Exception {
        given(providerService.registerProvider(any())).willReturn(buildProvider(1L));

        mockMvc.perform(post("/api/v1/providers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBodyJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.providerId").value(1))
                .andExpect(jsonPath("$.specialization").value("Cardiology"));
    }

    @Test
    @DisplayName("GET /api/v1/providers/{id} -> 200 OK")
    void getById_returns200() throws Exception {
        given(providerService.getProviderById(1L)).willReturn(buildProvider(1L));

        mockMvc.perform(get("/api/v1/providers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/providers/user/{userId} -> 200 OK")
    void getByUserId_returns200() throws Exception {
        given(providerService.getProviderByUserId(10L)).willReturn(buildProvider(1L));

        mockMvc.perform(get("/api/v1/providers/user/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/providers -> 200 OK with verified providers")
    void getAllVerified_returns200() throws Exception {
        given(providerService.getAllVerifiedProviders())
                .willReturn(List.of(buildProvider(1L), buildProvider(2L)));

        mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/providers/search?keyword=cardio -> 200 OK")
    void search_returns200() throws Exception {
        given(providerService.searchProviders("cardio"))
                .willReturn(List.of(buildProvider(1L)));

        mockMvc.perform(get("/api/v1/providers/search").param("keyword", "cardio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/providers/specialization/{spec} -> 200 OK")
    void getBySpecialization_returns200() throws Exception {
        given(providerService.getBySpecialization("Cardiology"))
                .willReturn(List.of(buildProvider(1L)));

        mockMvc.perform(get("/api/v1/providers/specialization/Cardiology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/providers/city/{city} -> 200 OK")
    void getByCity_returns200() throws Exception {
        given(providerService.getByCity("Mumbai"))
                .willReturn(List.of(buildProvider(1L)));

        mockMvc.perform(get("/api/v1/providers/city/Mumbai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/providers/top-rated -> 200 OK")
    void getTopRated_returns200() throws Exception {
        given(providerService.getTopRated(4.0))
                .willReturn(List.of(buildProvider(1L)));

        mockMvc.perform(get("/api/v1/providers/top-rated").param("minRating", "4.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/providers/fee-range -> 200 OK")
    void getByFeeRange_returns200() throws Exception {
        given(providerService.getByFeeRange(200.0, 1000.0))
                .willReturn(List.of(buildProvider(1L)));

        mockMvc.perform(get("/api/v1/providers/fee-range")
                        .param("min", "200.0").param("max", "1000.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/providers/{id}/verify -> 200 OK")
    void verify_returns200() throws Exception {
        Provider verified = buildProvider(1L);
        given(providerService.verifyProvider(1L)).willReturn(verified);

        mockMvc.perform(put("/api/v1/providers/1/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/providers/{id}/reject -> 200 OK")
    void reject_returns200() throws Exception {
        given(providerService.rejectProvider(1L)).willReturn(buildProvider(1L));

        mockMvc.perform(put("/api/v1/providers/1/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/providers/admin/pending -> 200 OK")
    void getPending_returns200() throws Exception {
        given(providerService.getPendingVerification())
                .willReturn(List.of(buildProvider(1L)));

        mockMvc.perform(get("/api/v1/providers/admin/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/providers/admin/all -> 200 OK")
    void getAll_returns200() throws Exception {
        given(providerService.getAllProviders())
                .willReturn(List.of(buildProvider(1L), buildProvider(2L)));

        mockMvc.perform(get("/api/v1/providers/admin/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("PUT /api/v1/providers/{id}/availability -> 200 OK")
    void setAvailability_returns200() throws Exception {
        given(providerService.setAvailability(1L, false)).willReturn(buildProvider(1L));

        mockMvc.perform(put("/api/v1/providers/1/availability").param("available", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/providers/{id}/rating -> 200 OK")
    void updateRating_returns200() throws Exception {
        given(providerService.updateRating(1L, 4.8, 35)).willReturn(buildProvider(1L));

        mockMvc.perform(put("/api/v1/providers/1/rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newAvgRating\":4.8,\"totalReviews\":35}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value(1));
    }

    @Test
    @DisplayName("DELETE /api/v1/providers/{id} -> 200 OK with message")
    void delete_returns200() throws Exception {
        willDoNothing().given(providerService).deleteProvider(1L);

        mockMvc.perform(delete("/api/v1/providers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Provider deleted successfully"));
    }
}

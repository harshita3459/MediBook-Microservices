package com.medibook.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medibook.review.dto.ReviewResponse;
import com.medibook.review.service.ReviewService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewController Tests")
class ReviewControllerTest {

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private ReviewController reviewController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(reviewController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private ReviewResponse buildResponse(Long id) {
        return ReviewResponse.builder()
                .reviewId(id)
                .appointmentId(10L)
                .patientId(1L)
                .providerId(5L)
                .rating(4)
                .comment("Great doctor!")
                .isVerified(true)
                .isAnonymous(false)
                .reviewDate(LocalDateTime.of(2026, 6, 1, 12, 0))
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/reviews -> 201 CREATED")
    void addReview_validRequest_returns201() throws Exception {
        given(reviewService.addReview(any())).willReturn(buildResponse(1L));

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentId\":10,\"patientId\":1,\"providerId\":5,\"rating\":4,\"comment\":\"Great!\",\"isAnonymous\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").value(1))
                .andExpect(jsonPath("$.rating").value(4));
    }

    @Test
    @DisplayName("GET /api/v1/reviews/appointment/{appointmentId} -> 200 OK")
    void getByAppointment_returns200() throws Exception {
        given(reviewService.getByAppointment(10L)).willReturn(buildResponse(1L));

        mockMvc.perform(get("/api/v1/reviews/appointment/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentId").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/reviews/provider/{providerId} -> 200 OK with list")
    void getByProvider_returns200() throws Exception {
        given(reviewService.getByProvider(5L))
                .willReturn(List.of(buildResponse(1L), buildResponse(2L)));

        mockMvc.perform(get("/api/v1/reviews/provider/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/reviews/provider/{providerId}/avg-rating -> 200 OK")
    void getAvgRating_returns200() throws Exception {
        given(reviewService.getAvgRating(5L)).willReturn(4.3);

        mockMvc.perform(get("/api/v1/reviews/provider/5/avg-rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgRating").value(4.3));
    }

    @Test
    @DisplayName("GET /api/v1/reviews/provider/{providerId}/count -> 200 OK")
    void getCount_returns200() throws Exception {
        given(reviewService.getReviewCount(5L)).willReturn(20L);

        mockMvc.perform(get("/api/v1/reviews/provider/5/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(20));
    }

    @Test
    @DisplayName("GET /api/v1/reviews/patient/{patientId} -> 200 OK")
    void getByPatient_returns200() throws Exception {
        given(reviewService.getByPatient(1L)).willReturn(List.of(buildResponse(1L)));

        mockMvc.perform(get("/api/v1/reviews/patient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/reviews -> 200 OK with all reviews")
    void getAll_returns200() throws Exception {
        given(reviewService.getAllReviews())
                .willReturn(List.of(buildResponse(1L), buildResponse(2L), buildResponse(3L)));

        mockMvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("PUT /api/v1/reviews/{id} -> 200 OK with updated review")
    void update_returns200() throws Exception {
        given(reviewService.updateReview(eq(1L), any())).willReturn(buildResponse(1L));

        mockMvc.perform(put("/api/v1/reviews/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Excellent!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(1));
    }

    @Test
    @DisplayName("DELETE /api/v1/reviews/{id} -> 200 OK with message")
    void delete_returns200() throws Exception {
        willDoNothing().given(reviewService).deleteReview(1L);

        mockMvc.perform(delete("/api/v1/reviews/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Review deleted successfully"));
    }
}

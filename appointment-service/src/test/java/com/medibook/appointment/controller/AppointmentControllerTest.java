package com.medibook.appointment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medibook.appointment.dto.AppointmentResponse;
import com.medibook.appointment.service.AppointmentService;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentController Tests")
class AppointmentControllerTest {

    @Mock
    private AppointmentService appointmentService;

    @InjectMocks
    private AppointmentController appointmentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(appointmentController)
                .setMessageConverters(converter)
                .build();
    }

    private AppointmentResponse buildResponse(Long id, String status) {
        return AppointmentResponse.builder()
                .appointmentId(id)
                .patientId(1L)
                .providerId(10L)
                .slotId(100L)
                .appointmentDate(LocalDate.of(2026, 6, 1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .status(status)
                .serviceType("CONSULTATION")
                .modeOfConsultation("IN_PERSON")
                .build();
    }

    private String bookRequestJson() {
        return """
                {
                  "patientId": 1,
                  "providerId": 10,
                  "slotId": 100,
                  "serviceType": "CONSULTATION",
                  "modeOfConsultation": "IN_PERSON",
                  "patientNotes": "Chest pain"
                }
                """;
    }

    @Test
    @DisplayName("POST /api/v1/appointments → 201 CREATED with booked appointment")
    void book_validRequest_returns201() throws Exception {
        given(appointmentService.bookAppointment(any())).willReturn(buildResponse(1L, "SCHEDULED"));

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(1))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @DisplayName("GET /api/v1/appointments/{id} → 200 OK with appointment")
    void getById_existing_returns200() throws Exception {
        given(appointmentService.getById(5L)).willReturn(buildResponse(5L, "SCHEDULED"));

        mockMvc.perform(get("/api/v1/appointments/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentId").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/appointments/patient/{patientId} → 200 OK with list")
    void getByPatient_returns200WithList() throws Exception {
        given(appointmentService.getByPatient(1L))
                .willReturn(List.of(buildResponse(1L, "SCHEDULED"), buildResponse(2L, "COMPLETED")));

        mockMvc.perform(get("/api/v1/appointments/patient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/appointments/patient/{patientId}/upcoming → 200 OK")
    void getUpcoming_returns200() throws Exception {
        given(appointmentService.getUpcomingByPatient(1L))
                .willReturn(List.of(buildResponse(1L, "SCHEDULED")));

        mockMvc.perform(get("/api/v1/appointments/patient/1/upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/appointments/provider/{providerId} → 200 OK")
    void getByProvider_returns200() throws Exception {
        given(appointmentService.getByProvider(10L))
                .willReturn(List.of(buildResponse(1L, "SCHEDULED")));

        mockMvc.perform(get("/api/v1/appointments/provider/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/appointments/provider/{providerId}/date?date=... → 200 OK")
    void getByProviderAndDate_returns200() throws Exception {
        given(appointmentService.getByProviderAndDate(eq(10L), any(LocalDate.class)))
                .willReturn(List.of(buildResponse(1L, "SCHEDULED")));

        mockMvc.perform(get("/api/v1/appointments/provider/10/date")
                        .param("date", "2026-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/appointments → 200 OK with all appointments")
    void getAll_returns200() throws Exception {
        given(appointmentService.getAllAppointments())
                .willReturn(List.of(buildResponse(1L, "SCHEDULED"), buildResponse(2L, "CANCELLED")));

        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/appointments/provider/{providerId}/count → 200 OK with count")
    void getCount_returns200WithCount() throws Exception {
        given(appointmentService.countByProvider(10L)).willReturn(42L);

        mockMvc.perform(get("/api/v1/appointments/provider/10/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(42));
    }

    @Test
    @DisplayName("PUT /api/v1/appointments/{id}/cancel → 200 OK with cancelled appointment")
    void cancel_returns200WithCancelledStatus() throws Exception {
        given(appointmentService.cancelAppointment(eq(1L), any())).willReturn(buildResponse(1L, "CANCELLED"));

        mockMvc.perform(put("/api/v1/appointments/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Patient request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PUT /api/v1/appointments/{id}/reschedule → 200 OK")
    void reschedule_returns200() throws Exception {
        given(appointmentService.rescheduleAppointment(eq(1L), eq(200L)))
                .willReturn(buildResponse(1L, "RESCHEDULED"));

        mockMvc.perform(put("/api/v1/appointments/1/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newSlotId\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentId").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/appointments/{id}/complete → 200 OK with COMPLETED status")
    void complete_returns200WithCompletedStatus() throws Exception {
        given(appointmentService.completeAppointment(1L)).willReturn(buildResponse(1L, "COMPLETED"));

        mockMvc.perform(put("/api/v1/appointments/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}

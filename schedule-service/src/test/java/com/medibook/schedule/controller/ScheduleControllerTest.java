package com.medibook.schedule.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medibook.schedule.entity.AvailabilitySlot;
import com.medibook.schedule.service.ScheduleService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleController Tests")
class ScheduleControllerTest {

    @Mock
    private ScheduleService scheduleService;

    @InjectMocks
    private ScheduleController scheduleController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(scheduleController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private AvailabilitySlot buildSlot(Long id) {
        return AvailabilitySlot.builder()
                .slotId(id)
                .providerId(5L)
                .slotDate(LocalDate.of(2026, 6, 15))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .durationMinutes(30)
                .isBooked(false)
                .isBlocked(false)
                .recurrence("NONE")
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/slots/add -> 201 CREATED")
    void addSlot_validRequest_returns201() throws Exception {
        given(scheduleService.addSlot(anyLong(), any(LocalDate.class), any(LocalTime.class), any(LocalTime.class), any()))
                .willReturn(buildSlot(1L));

        mockMvc.perform(post("/api/v1/slots/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":5,\"date\":\"2026-06-15\",\"startTime\":\"10:00:00\",\"endTime\":\"10:30:00\",\"recurrence\":\"NONE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotId").value(1))
                .andExpect(jsonPath("$.providerId").value(5));
    }

    @Test
    @DisplayName("POST /api/v1/slots/bulk -> 201 CREATED with list")
    void addBulkSlots_returns201() throws Exception {
        given(scheduleService.addBulkSlots(anyList()))
                .willReturn(List.of(buildSlot(1L), buildSlot(2L)));

        mockMvc.perform(post("/api/v1/slots/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"providerId\":5,\"slotDate\":\"2026-06-15\",\"startTime\":\"10:00:00\",\"endTime\":\"10:30:00\"}]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/slots/recurring -> 201 CREATED with summary")
    void generateRecurring_returns201WithSummary() throws Exception {
        given(scheduleService.generateRecurringSlots(anyLong(), any(), any(), any(), any(), anyInt(), anyString()))
                .willReturn(List.of(buildSlot(1L), buildSlot(2L), buildSlot(3L)));

        mockMvc.perform(post("/api/v1/slots/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":5,\"startDate\":\"2026-06-01\",\"endDate\":\"2026-06-30\"," +
                                "\"startTime\":\"09:00:00\",\"endTime\":\"17:00:00\"," +
                                "\"durationMinutes\":30,\"recurrence\":\"MON_WED_FRI\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotsCreated").value(3))
                .andExpect(jsonPath("$.message").value("Recurring slots generated successfully"));
    }

    @Test
    @DisplayName("GET /api/v1/slots/provider/{providerId} -> 200 OK")
    void getByProvider_returns200() throws Exception {
        given(scheduleService.getSlotsByProvider(5L))
                .willReturn(List.of(buildSlot(1L), buildSlot(2L)));

        mockMvc.perform(get("/api/v1/slots/provider/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/slots/available -> 200 OK")
    void getAvailable_returns200() throws Exception {
        given(scheduleService.getAvailableSlots(eq(5L), any(LocalDate.class)))
                .willReturn(List.of(buildSlot(1L)));

        mockMvc.perform(get("/api/v1/slots/available")
                        .param("providerId", "5")
                        .param("date", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/slots/available/range -> 200 OK")
    void getAvailableInRange_returns200() throws Exception {
        given(scheduleService.getAvailableSlotsInRange(eq(5L), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(buildSlot(1L), buildSlot(2L)));

        mockMvc.perform(get("/api/v1/slots/available/range")
                        .param("providerId", "5")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/slots/{id} -> 200 OK when slot exists")
    void getById_existing_returns200() throws Exception {
        given(scheduleService.getSlotById(1L)).willReturn(Optional.of(buildSlot(1L)));

        mockMvc.perform(get("/api/v1/slots/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/slots/{id} -> 404 when slot does not exist")
    void getById_notFound_returns404() throws Exception {
        given(scheduleService.getSlotById(999L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/slots/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/slots/{id}/book -> 200 OK")
    void bookSlot_returns200() throws Exception {
        AvailabilitySlot booked = buildSlot(1L);
        booked.setBooked(true);
        booked.setAppointmentId(20L);
        given(scheduleService.bookSlot(1L, 20L)).willReturn(booked);

        mockMvc.perform(put("/api/v1/slots/1/book").param("appointmentId", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/slots/{id}/release -> 200 OK")
    void releaseSlot_returns200() throws Exception {
        given(scheduleService.releaseSlot(1L)).willReturn(buildSlot(1L));

        mockMvc.perform(put("/api/v1/slots/1/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/slots/{id}/block -> 200 OK")
    void blockSlot_returns200() throws Exception {
        AvailabilitySlot blocked = buildSlot(1L);
        blocked.setBlocked(true);
        given(scheduleService.blockSlot(1L)).willReturn(blocked);

        mockMvc.perform(put("/api/v1/slots/1/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/slots/{id}/unblock -> 200 OK")
    void unblockSlot_returns200() throws Exception {
        given(scheduleService.unblockSlot(1L)).willReturn(buildSlot(1L));

        mockMvc.perform(put("/api/v1/slots/1/unblock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/slots/{id} -> 200 OK with updated slot")
    void updateSlot_returns200() throws Exception {
        given(scheduleService.updateSlot(eq(1L), any(), any(), any())).willReturn(buildSlot(1L));

        mockMvc.perform(put("/api/v1/slots/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-06-16\",\"startTime\":\"11:00:00\",\"endTime\":\"11:30:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value(1));
    }

    @Test
    @DisplayName("DELETE /api/v1/slots/{id} -> 200 OK with message")
    void deleteSlot_returns200() throws Exception {
        willDoNothing().given(scheduleService).deleteSlot(1L);

        mockMvc.perform(delete("/api/v1/slots/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Slot deleted successfully"));
    }
}

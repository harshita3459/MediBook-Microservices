package com.medibook.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medibook.notification.dto.NotificationResponse;
import com.medibook.notification.entity.Notification;
import com.medibook.notification.service.NotificationService;
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
@DisplayName("NotificationController Tests")
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private NotificationResponse buildResponse(Long id) {
        return NotificationResponse.builder()
                .notificationId(id)
                .recipientId(1L)
                .type("BOOKING_CONFIRMED")
                .title("Appointment Confirmed")
                .message("Your appointment has been confirmed.")
                .channel("APP")
                .relatedId(10L)
                .relatedType("APPOINTMENT")
                .isRead(false)
                .sentAt(LocalDateTime.of(2026, 6, 1, 9, 0))
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/notifications -> 201 CREATED")
    void send_validRequest_returns201() throws Exception {
        given(notificationService.send(anyLong(), any(), anyString(), anyString(), any(), any(), any()))
                .willReturn(buildResponse(1L));

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":1,\"type\":\"BOOKING_CONFIRMED\"," +
                                "\"title\":\"Appointment Confirmed\",\"message\":\"Your appointment is confirmed.\"," +
                                "\"channel\":\"APP\",\"relatedId\":10,\"relatedType\":\"APPOINTMENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificationId").value(1))
                .andExpect(jsonPath("$.type").value("BOOKING_CONFIRMED"));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/appointment-event -> 200 OK")
    void handleAppointmentEvent_returns200() throws Exception {
        given(notificationService.send(anyLong(), any(), anyString(), anyString(), any(), any(), any()))
                .willReturn(buildResponse(1L));

        mockMvc.perform(post("/api/v1/notifications/appointment-event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"BOOKING_CONFIRMED\",\"patientId\":1,\"appointmentId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event processed"));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/payment-event -> 200 OK")
    void handlePaymentEvent_returns200() throws Exception {
        given(notificationService.send(anyLong(), any(), anyString(), anyString(), any(), any(), any()))
                .willReturn(buildResponse(1L));

        mockMvc.perform(post("/api/v1/notifications/payment-event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"PAYMENT_RECEIPT\",\"patientId\":1,\"paymentId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment event processed"));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/bulk -> 200 OK")
    void sendBulk_returns200() throws Exception {
        willDoNothing().given(notificationService).sendBulk(anyList(), anyString(), anyString(), any());

        mockMvc.perform(post("/api/v1/notifications/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientIds\":[1,2,3],\"title\":\"Maintenance\",\"message\":\"System down at midnight.\",\"channel\":\"APP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Bulk notification sent"))
                .andExpect(jsonPath("$.recipients").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/recipient/{id} -> 200 OK")
    void getByRecipient_returns200() throws Exception {
        given(notificationService.getByRecipient(1L))
                .willReturn(List.of(buildResponse(1L), buildResponse(2L)));

        mockMvc.perform(get("/api/v1/notifications/recipient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/recipient/{id}/unread -> 200 OK")
    void getUnread_returns200() throws Exception {
        given(notificationService.getUnreadByRecipient(1L))
                .willReturn(List.of(buildResponse(1L)));

        mockMvc.perform(get("/api/v1/notifications/recipient/1/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/recipient/{id}/count -> 200 OK")
    void getUnreadCount_returns200() throws Exception {
        given(notificationService.getUnreadCount(1L)).willReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/recipient/1/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
    }

    @Test
    @DisplayName("PUT /api/v1/notifications/{id}/read -> 200 OK")
    void markAsRead_returns200() throws Exception {
        NotificationResponse read = buildResponse(1L);
        read.setRead(true);
        given(notificationService.markAsRead(1L)).willReturn(read);

        mockMvc.perform(put("/api/v1/notifications/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/notifications/recipient/{id}/read-all -> 200 OK")
    void markAllAsRead_returns200() throws Exception {
        given(notificationService.markAllAsRead(1L)).willReturn(5);

        mockMvc.perform(put("/api/v1/notifications/recipient/1/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Marked 5 notifications as read"));
    }

    @Test
    @DisplayName("DELETE /api/v1/notifications/{id} -> 200 OK")
    void delete_returns200() throws Exception {
        willDoNothing().given(notificationService).deleteNotification(1L);

        mockMvc.perform(delete("/api/v1/notifications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification deleted"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications -> 200 OK with all notifications")
    void getAll_returns200() throws Exception {
        given(notificationService.getAll())
                .willReturn(List.of(buildResponse(1L), buildResponse(2L), buildResponse(3L)));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }
}

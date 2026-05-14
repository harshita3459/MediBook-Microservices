package com.medibook.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medibook.payment.dto.*;
import com.medibook.payment.entity.Payment.PaymentMode;
import com.medibook.payment.entity.Payment.PaymentStatus;
import com.medibook.payment.service.PaymentService;
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
@DisplayName("PaymentController Tests")
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private PaymentResponse buildPaymentResponse(Long id) {
        return PaymentResponse.builder()
                .paymentId(id)
                .appointmentId(10L)
                .patientId(1L)
                .providerId(5L)
                .amount(500.0)
                .status(PaymentStatus.PAID)
                .mode(PaymentMode.UPI)
                .currency("INR")
                .paidAt(LocalDateTime.of(2026, 6, 1, 10, 0))
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/payments → 201 CREATED")
    void process_validRequest_returns201() throws Exception {
        given(paymentService.processPayment(any())).willReturn(buildPaymentResponse(1L));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentId\":10,\"patientId\":1,\"providerId\":5,\"amount\":500.0,\"mode\":\"UPI\",\"currency\":\"INR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(1))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/appointment/{appointmentId} → 200 OK")
    void getByAppointment_returns200() throws Exception {
        given(paymentService.getPaymentByAppointment(10L)).willReturn(buildPaymentResponse(1L));

        mockMvc.perform(get("/api/v1/payments/appointment/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentId").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/payments/patient/{patientId} → 200 OK with list")
    void getByPatient_returns200() throws Exception {
        given(paymentService.getPaymentsByPatient(1L))
                .willReturn(List.of(buildPaymentResponse(1L), buildPaymentResponse(2L)));

        mockMvc.perform(get("/api/v1/payments/patient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/payments/patient/{patientId}/history → 200 OK")
    void getHistory_returns200() throws Exception {
        given(paymentService.getPaymentHistory(1L))
                .willReturn(List.of(buildPaymentResponse(1L)));

        mockMvc.perform(get("/api/v1/payments/patient/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id}/status → 200 OK with status string")
    void getStatus_returns200WithStatus() throws Exception {
        given(paymentService.getPaymentStatus(1L)).willReturn("PAID");

        mockMvc.perform(get("/api/v1/payments/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/{id}/refund → 200 OK with refunded payment")
    void refund_returns200() throws Exception {
        PaymentResponse refunded = buildPaymentResponse(1L);
        refunded.setStatus(PaymentStatus.REFUNDED);
        given(paymentService.refundPayment(eq(1L), any())).willReturn(refunded);

        mockMvc.perform(post("/api/v1/payments/1/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Appointment cancelled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @DisplayName("PUT /api/v1/payments/{id}/status → 200 OK with updated payment")
    void updateStatus_returns200() throws Exception {
        given(paymentService.updatePaymentStatus(1L, "PAID")).willReturn(buildPaymentResponse(1L));

        mockMvc.perform(put("/api/v1/payments/1/status")
                        .param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id}/invoice → 200 OK with invoice")
    void invoice_returns200() throws Exception {
        InvoiceResponse invoice = InvoiceResponse.builder()
                .invoiceNumber("PAY-1-10")
                .paymentId(1L)
                .appointmentId(10L)
                .amount(500.0)
                .currency("INR")
                .status("PAID")
                .build();
        given(paymentService.generateInvoice(1L)).willReturn(invoice);

        mockMvc.perform(get("/api/v1/payments/1/invoice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("PAY-1-10"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/provider/{providerId}/revenue → 200 OK")
    void getTotalRevenue_returns200() throws Exception {
        given(paymentService.getTotalRevenue(5L)).willReturn(25000.0);

        mockMvc.perform(get("/api/v1/payments/provider/5/revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(25000.0));
    }

    @Test
    @DisplayName("GET /api/v1/payments/provider/{providerId}/earnings → 200 OK")
    void getEarnings_returns200() throws Exception {
        EarningsSummary summary = EarningsSummary.builder()
                .providerId(5L)
                .totalCollected(25000.0)
                .totalRefunded(500.0)
                .pendingAmount(1000.0)
                .totalTransactions(50L)
                .build();
        given(paymentService.getEarningsSummary(5L)).willReturn(summary);

        mockMvc.perform(get("/api/v1/payments/provider/5/earnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value(5))
                .andExpect(jsonPath("$.totalTransactions").value(50));
    }

    @Test
    @DisplayName("GET /api/v1/payments → 200 OK with all payments")
    void getAll_returns200() throws Exception {
        given(paymentService.getAllPayments())
                .willReturn(List.of(buildPaymentResponse(1L), buildPaymentResponse(2L)));

        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}

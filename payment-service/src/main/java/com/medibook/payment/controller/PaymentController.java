package com.medibook.payment.controller;

import com.medibook.payment.dto.*;
import com.medibook.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PaymentResource — REST API for payment lifecycle.
 * Base URL: /api/v1/payments   Port: 8085
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Process payments, refunds, invoices and provider earnings")
public class PaymentController {

    private final PaymentService paymentService;

    // ── POST /api/v1/payments ─────────────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Process a new payment for an appointment")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PaymentResponse> process(
            @Valid @RequestBody ProcessPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.processPayment(request));
    }

    // ── GET /api/v1/payments/appointment/{appointmentId} ──────────────────────
    @GetMapping("/appointment/{appointmentId}")
    @Operation(summary = "Get payment by appointment ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PaymentResponse> getByAppointment(
            @PathVariable Long appointmentId) {
        return ResponseEntity.ok(paymentService.getPaymentByAppointment(appointmentId));
    }

    // ── GET /api/v1/payments/patient/{patientId} ──────────────────────────────
    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get all payments for a patient")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<PaymentResponse>> getByPatient(
            @PathVariable Long patientId) {
        return ResponseEntity.ok(paymentService.getPaymentsByPatient(patientId));
    }

    // ── GET /api/v1/payments/patient/{patientId}/history ──────────────────────
    @GetMapping("/patient/{patientId}/history")
    @Operation(summary = "Get full payment history for a patient")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<PaymentResponse>> getHistory(
            @PathVariable Long patientId) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(patientId));
    }

    // ── GET /api/v1/payments/{id}/status ─────────────────────────────────────
    @GetMapping("/{id}/status")
    @Operation(summary = "Get payment status by payment ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> getStatus(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("status", paymentService.getPaymentStatus(id)));
    }

    // ── POST /api/v1/payments/{id}/refund ────────────────────────────────────
    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund a payment — triggered on appointment cancellation")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PaymentResponse> refund(
            @PathVariable Long id,
            @RequestBody(required = false) RefundRequest body) {
        String reason = (body != null) ? body.getReason() : null;
        return ResponseEntity.ok(paymentService.refundPayment(id, reason));
    }

    // ── PUT /api/v1/payments/{id}/status ─────────────────────────────────────
    /** Called by payment gateway webhook to confirm or fail a payment */
    @PutMapping("/{id}/status")
    @Operation(summary = "Update payment status — called by gateway webhook or admin")
    public ResponseEntity<PaymentResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(paymentService.updatePaymentStatus(id, status));
    }

    // ── GET /api/v1/payments/{id}/invoice ────────────────────────────────────
    @GetMapping("/{id}/invoice")
    @Operation(summary = "Generate invoice for a paid appointment")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<InvoiceResponse> invoice(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.generateInvoice(id));
    }

    // ── GET /api/v1/payments/provider/{providerId}/revenue ───────────────────
    @GetMapping("/provider/{providerId}/revenue")
    @Operation(summary = "Get total revenue collected by a provider")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Double>> getTotalRevenue(
            @PathVariable Long providerId) {
        return ResponseEntity.ok(Map.of("totalRevenue", paymentService.getTotalRevenue(providerId)));
    }

    // ── GET /api/v1/payments/provider/{providerId}/earnings ──────────────────
    @GetMapping("/provider/{providerId}/earnings")
    @Operation(summary = "Get full earnings summary for a provider")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<EarningsSummary> getEarnings(
            @PathVariable Long providerId) {
        return ResponseEntity.ok(paymentService.getEarningsSummary(providerId));
    }

    // ── GET /api/v1/payments (admin) ──────────────────────────────────────────
    @GetMapping
    @Operation(summary = "Get ALL payments — admin only")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<PaymentResponse>> getAll() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }
}

package com.medibook.appointment.controller;

import com.medibook.appointment.dto.AppointmentResponse;
import com.medibook.appointment.dto.BookAppointmentRequest;
import com.medibook.appointment.dto.CancelRequest;
import com.medibook.appointment.dto.RescheduleRequest;
import com.medibook.appointment.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AppointmentResource — REST API for the appointment lifecycle.
 * Base URL: /api/v1/appointments   Port: 8084
 */
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Appointments", description = "Book, cancel, reschedule, and complete appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    // ── POST /api/v1/appointments ─────────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Book a new appointment")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AppointmentResponse> book(
            @Valid @RequestBody BookAppointmentRequest request) {
        AppointmentResponse response = appointmentService.bookAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /api/v1/appointments/{id} ─────────────────────────────────────────
    @GetMapping("/{id}")
    @Operation(summary = "Get appointment by ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AppointmentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.getById(id));
    }

    // ── GET /api/v1/appointments/patient/{patientId} ──────────────────────────
    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get all appointments for a patient")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AppointmentResponse>> getByPatient(
            @PathVariable Long patientId) {
        return ResponseEntity.ok(appointmentService.getByPatient(patientId));
    }

    // ── GET /api/v1/appointments/patient/{patientId}/upcoming ─────────────────
    @GetMapping("/patient/{patientId}/upcoming")
    @Operation(summary = "Get upcoming appointments for a patient")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AppointmentResponse>> getUpcoming(
            @PathVariable Long patientId) {
        return ResponseEntity.ok(appointmentService.getUpcomingByPatient(patientId));
    }

    // ── GET /api/v1/appointments/provider/{providerId} ────────────────────────
    @GetMapping("/provider/{providerId}")
    @Operation(summary = "Get all appointments for a provider")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AppointmentResponse>> getByProvider(
            @PathVariable Long providerId) {
        return ResponseEntity.ok(appointmentService.getByProvider(providerId));
    }

    // ── GET /api/v1/appointments/provider/{providerId}/date?date= ─────────────
    @GetMapping("/provider/{providerId}/date")
    @Operation(summary = "Get provider appointments for a specific date")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AppointmentResponse>> getByProviderAndDate(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(appointmentService.getByProviderAndDate(providerId, date));
    }

    // ── GET /api/v1/appointments (admin) ──────────────────────────────────────
    @GetMapping
    @Operation(summary = "Get ALL appointments — admin only")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AppointmentResponse>> getAll() {
        return ResponseEntity.ok(appointmentService.getAllAppointments());
    }

    // ── GET /api/v1/appointments/provider/{providerId}/count ──────────────────
    @GetMapping("/provider/{providerId}/count")
    @Operation(summary = "Count total appointments for a provider")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Long>> getCount(@PathVariable Long providerId) {
        return ResponseEntity.ok(Map.of("count", appointmentService.countByProvider(providerId)));
    }

    // ── PUT /api/v1/appointments/{id}/cancel ──────────────────────────────────
    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an appointment")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AppointmentResponse> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelRequest body) {
        String reason = (body != null) ? body.getReason() : null;
        return ResponseEntity.ok(appointmentService.cancelAppointment(id, reason));
    }

    // ── PUT /api/v1/appointments/{id}/reschedule ──────────────────────────────
    @PutMapping("/{id}/reschedule")
    @Operation(summary = "Reschedule appointment to a new slot")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AppointmentResponse> reschedule(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleRequest body) {
        return ResponseEntity.ok(
            appointmentService.rescheduleAppointment(id, body.getNewSlotId()));
    }

    // ── PUT /api/v1/appointments/{id}/complete ────────────────────────────────
    /** Called by provider after consultation is done — unlocks review for patient */
    @PutMapping("/{id}/complete")
    @Operation(summary = "Mark appointment as completed (provider action)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AppointmentResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.completeAppointment(id));
    }
}
package com.medibook.record.controller;

import com.medibook.record.dto.*;
import com.medibook.record.service.RecordService;
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
 * RecordController — REST API for Electronic Medical Records.
 * Base URL : /api/v1/records   Port: 8088
 */
@RestController
@RequestMapping("/api/v1/records")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Medical Records", description = "Electronic health records, prescriptions and follow-up tracking")
public class RecordController {

    private final RecordService recordService;

    // ── POST /api/v1/records ──────────────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Create medical record — provider only, appointment must be COMPLETED")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RecordResponse> create(@Valid @RequestBody CreateRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recordService.createRecord(request));
    }

    // ── GET /api/v1/records/{id} ──────────────────────────────────────────────
    @GetMapping("/{id}")
    @Operation(summary = "Get record by record ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RecordResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(recordService.getRecordById(id));
    }

    // ── GET /api/v1/records/appointment/{appointmentId} ───────────────────────
    @GetMapping("/appointment/{appointmentId}")
    @Operation(summary = "Get record by appointment ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RecordResponse> getByAppointment(@PathVariable Long appointmentId) {
        return ResponseEntity.ok(recordService.getRecordByAppointment(appointmentId));
    }

    // ── GET /api/v1/records/patient/{patientId} ───────────────────────────────
    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get all records for a patient")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<RecordResponse>> getByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(recordService.getRecordsByPatient(patientId));
    }

    // ── GET /api/v1/records/patient/{patientId}/count ────────────────────────
    @GetMapping("/patient/{patientId}/count")
    @Operation(summary = "Get total record count for a patient")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Long>> getCount(@PathVariable Long patientId) {
        return ResponseEntity.ok(Map.of("count", recordService.getRecordCount(patientId)));
    }

    // ── GET /api/v1/records/provider/{providerId} ─────────────────────────────
    @GetMapping("/provider/{providerId}")
    @Operation(summary = "Get all records created by a provider")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<RecordResponse>> getByProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(recordService.getRecordsByProvider(providerId));
    }

    // ── GET /api/v1/records/provider/{providerId}/followups ───────────────────
    @GetMapping("/provider/{providerId}/followups")
    @Operation(summary = "Get all records with follow-up dates for a provider")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<RecordResponse>> getProviderFollowUps(@PathVariable Long providerId) {
        return ResponseEntity.ok(recordService.getProviderFollowUps(providerId));
    }

    // ── GET /api/v1/records/followups?date=YYYY-MM-DD ─────────────────────────
    @GetMapping("/followups")
    @Operation(summary = "Get records with follow-up due on a specific date (admin/internal)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<RecordResponse>> getFollowUps(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(recordService.getFollowUpRecords(date));
    }

    // ── GET /api/v1/records (admin) ───────────────────────────────────────────
    @GetMapping
    @Operation(summary = "Get ALL records — admin audit access only")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<RecordResponse>> getAll() {
        return ResponseEntity.ok(recordService.getAllRecords());
    }

    // ── PUT /api/v1/records/{id} ──────────────────────────────────────────────
    @PutMapping("/{id}")
    @Operation(summary = "Update a record — allowed within 48 hours of creation")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RecordResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRecordRequest request) {
        return ResponseEntity.ok(recordService.updateRecord(id, request));
    }

    // ── PUT /api/v1/records/{id}/attach ───────────────────────────────────────
    @PutMapping("/{id}/attach")
    @Operation(summary = "Attach a document URL to a record (S3 upload link)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RecordResponse> attach(
            @PathVariable Long id,
            @Valid @RequestBody AttachDocumentRequest request) {
        return ResponseEntity.ok(recordService.attachDocument(id, request.getAttachmentUrl()));
    }

    // ── DELETE /api/v1/records/{id} ───────────────────────────────────────────
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a record — admin only")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        recordService.deleteRecord(id);
        return ResponseEntity.ok(Map.of("message", "Medical record deleted successfully"));
    }
}

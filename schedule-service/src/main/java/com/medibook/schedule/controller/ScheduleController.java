package com.medibook.schedule.controller;

import com.medibook.schedule.entity.AvailabilitySlot;
import com.medibook.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * ScheduleResource — REST endpoints for slot management.
 *
 * Base URL: /api/v1/slots
 * Port:     8083
 *
 * Public endpoints (no token):
 *   GET /available — patients browse available slots
 *
 * Provider endpoints (PROVIDER token):
 *   POST /add, /bulk, /recurring — create slots
 *   PUT /{id}/block, /{id}/unblock, /{id} — manage slots
 *   DELETE /{id} — remove slots
 *
 * Internal endpoints (service-to-service, no user token needed):
 *   PUT /{id}/book   — called by appointment-service when patient books
 *   PUT /{id}/release — called by appointment-service on cancellation
 */
@RestController
@RequestMapping("/api/v1/slots")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Schedule", description = "Provider availability slot management and booking")
public class ScheduleController {

    private final ScheduleService scheduleService;

    // ── POST /api/v1/slots/add ────────────────────────────────────────────────
    /**
     * Provider adds a single availability slot to their calendar.
     */
    @PostMapping("/add")
    @Operation(summary = "Add a single slot (PROVIDER role)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AvailabilitySlot> addSlot(@Valid @RequestBody AddSlotBody body) {
        AvailabilitySlot slot = scheduleService.addSlot(
            body.providerId(), body.date(),
            body.startTime(), body.endTime(),
            body.recurrence());
        return ResponseEntity.status(HttpStatus.CREATED).body(slot);
    }

    // ── POST /api/v1/slots/bulk ───────────────────────────────────────────────
    /**
     * Provider uploads a list of slots at once.
     * Useful when setting up a new month's schedule in one API call.
     */
    @PostMapping("/bulk")
    @Operation(summary = "Add multiple slots at once (PROVIDER role)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AvailabilitySlot>> addBulkSlots(
            @RequestBody List<AvailabilitySlot> slots) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduleService.addBulkSlots(slots));
    }

    // ── POST /api/v1/slots/recurring ──────────────────────────────────────────
    /**
     * Auto-generates recurring slots.
     *
     * Example request body:
     * {
     *   "providerId": 7,
     *   "startDate": "2026-05-01",
     *   "endDate": "2026-05-31",
     *   "startTime": "09:00",
     *   "endTime": "17:00",
     *   "durationMinutes": 30,
     *   "recurrence": "MON_WED_FRI"
     * }
     * → Creates up to ~200 slots automatically
     */
    @PostMapping("/recurring")
    @Operation(summary = "Generate recurring slots across a date range (PROVIDER role)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> generateRecurring(
            @Valid @RequestBody RecurringBody body) {

        List<AvailabilitySlot> slots = scheduleService.generateRecurringSlots(
            body.providerId(),
            body.startDate(), body.endDate(),
            body.startTime(), body.endTime(),
            body.durationMinutes(),
            body.recurrence());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message",      "Recurring slots generated successfully",
            "slotsCreated", slots.size(),
            "pattern",      body.recurrence(),
            "dateRange",    body.startDate() + " to " + body.endDate()
        ));
    }

    // ── GET /api/v1/slots/provider/{providerId} ───────────────────────────────
    /**
     * Get ALL slots for a provider (including booked and blocked).
     * Used by provider's own calendar dashboard.
     */
    @GetMapping("/provider/{providerId}")
    @Operation(summary = "Get all slots for a provider (PROVIDER role)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AvailabilitySlot>> getByProvider(
            @PathVariable Long providerId) {
        return ResponseEntity.ok(scheduleService.getSlotsByProvider(providerId));
    }

    // ── GET /api/v1/slots/available?providerId=7&date=2026-05-01 ─────────────
    /**
     * Get only AVAILABLE slots for a provider on a specific date.
     * PUBLIC — patients call this to see what they can book.
     * Only returns unbooked, unblocked slots.
     */
    @GetMapping("/available")
    @Operation(summary = "Get available slots for a provider on a date (public)")
    public ResponseEntity<List<AvailabilitySlot>> getAvailable(
            @RequestParam Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(scheduleService.getAvailableSlots(providerId, date));
    }

    // ── GET /api/v1/slots/available/range?providerId=7&startDate=...&endDate=... ─
    /**
     * Get available slots across a date range.
     * Used by patient calendar view to show which days have open slots.
     */
    @GetMapping("/available/range")
    @Operation(summary = "Get available slots across a date range (public)")
    public ResponseEntity<List<AvailabilitySlot>> getAvailableInRange(
            @RequestParam Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(
            scheduleService.getAvailableSlotsInRange(providerId, startDate, endDate));
    }

    // ── GET /api/v1/slots/{id} ────────────────────────────────────────────────
    @GetMapping("/{id}")
    @Operation(summary = "Get a slot by ID")
    public ResponseEntity<AvailabilitySlot> getById(@PathVariable Long id) {
        return scheduleService.getSlotById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── PUT /api/v1/slots/{id}/book ───────────────────────────────────────────
    /**
     * INTERNAL endpoint — called ONLY by appointment-service.
     * Books the slot and stores the appointmentId on it.
     * Protected by optimistic locking — concurrent calls are safe.
     */
    @PutMapping("/{id}/book")
    @Operation(summary = "Book a slot (internal — called by appointment-service)")
    public ResponseEntity<AvailabilitySlot> bookSlot(
            @PathVariable Long id,
            @RequestParam Long appointmentId) {
        return ResponseEntity.ok(scheduleService.bookSlot(id, appointmentId));
    }

    // ── PUT /api/v1/slots/{id}/release ────────────────────────────────────────
    /**
     * INTERNAL endpoint — called by appointment-service on cancellation.
     * Releases the slot back to AVAILABLE so patients can book it again.
     */
    @PutMapping("/{id}/release")
    @Operation(summary = "Release a booked slot (internal — called by appointment-service)")
    public ResponseEntity<AvailabilitySlot> releaseSlot(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.releaseSlot(id));
    }

    // ── PUT /api/v1/slots/{id}/block ──────────────────────────────────────────
    /**
     * Provider blocks a slot (lunch break, personal appointment, etc.).
     * Blocked slots disappear from patient search immediately.
     */
    @PutMapping("/{id}/block")
    @Operation(summary = "Block a slot (PROVIDER role)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AvailabilitySlot> blockSlot(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.blockSlot(id));
    }

    // ── PUT /api/v1/slots/{id}/unblock ────────────────────────────────────────
    @PutMapping("/{id}/unblock")
    @Operation(summary = "Unblock a slot (PROVIDER role)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AvailabilitySlot> unblockSlot(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.unblockSlot(id));
    }

    // ── PUT /api/v1/slots/{id} ────────────────────────────────────────────────
    @PutMapping("/{id}")
    @Operation(summary = "Update a slot's time or date (PROVIDER role)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AvailabilitySlot> updateSlot(
            @PathVariable Long id,
            @RequestBody UpdateSlotBody body) {
        return ResponseEntity.ok(
            scheduleService.updateSlot(id, body.date(), body.startTime(), body.endTime()));
    }

    // ── DELETE /api/v1/slots/{id} ─────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a slot (PROVIDER role)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> deleteSlot(@PathVariable Long id) {
        scheduleService.deleteSlot(id);
        return ResponseEntity.ok(Map.of("message", "Slot deleted successfully"));
    }

    // ── Request body records ──────────────────────────────────────────────────

    record AddSlotBody(
        @NotNull Long      providerId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        String recurrence
    ) {}

    record RecurringBody(
        @NotNull Long      providerId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        int    durationMinutes,
        @NotNull String recurrence
    ) {}

    record UpdateSlotBody(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
    ) {}
}
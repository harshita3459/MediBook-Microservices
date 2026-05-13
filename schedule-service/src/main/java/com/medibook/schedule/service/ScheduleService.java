package com.medibook.schedule.service;

import com.medibook.schedule.entity.AvailabilitySlot;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * ScheduleService — business contract for all slot operations.
 *
 * Declaring an interface separately from the implementation:
 *   1. Callers depend on the interface, not the concrete class (loose coupling)
 *   2. Easy to mock in unit tests — just mock this interface
 *   3. Allows swapping implementations (e.g. add Redis caching layer) without
 *      changing any controller code
 */
public interface ScheduleService {

    // ── Single slot operations ─────────────────────────────────────────────────

    AvailabilitySlot addSlot(Long providerId, LocalDate date,
                             LocalTime startTime, LocalTime endTime,
                             String recurrence);

    Optional<AvailabilitySlot> getSlotById(Long slotId);

    AvailabilitySlot updateSlot(Long slotId, LocalDate date,
                                LocalTime startTime, LocalTime endTime);

    void deleteSlot(Long slotId);

    // ── Bulk / recurring creation ──────────────────────────────────────────────

    /**
     * Saves a list of pre-built slots, skipping any that overlap existing ones.
     */
    List<AvailabilitySlot> addBulkSlots(List<AvailabilitySlot> slots);

    /**
     * Auto-generates slots across a date range using a recurrence pattern.
     *
     * @param providerId      which provider's calendar
     * @param startDate       first date in the range (inclusive)
     * @param endDate         last date in the range (inclusive)
     * @param startTime       first slot of each matching day starts at this time
     * @param endTime         last slot of each matching day ends by this time
     * @param durationMinutes each slot's length
     * @param recurrence      "DAILY" | "WEEKDAYS" | "WEEKLY" | "MON_WED_FRI" | "TUE_THU" | "WEEKENDS"
     * @return all slots that were actually created (conflicts are skipped)
     */
    List<AvailabilitySlot> generateRecurringSlots(
            Long providerId,
            LocalDate startDate, LocalDate endDate,
            LocalTime startTime, LocalTime endTime,
            int durationMinutes,
            String recurrence);

    // ── Retrieval ──────────────────────────────────────────────────────────────

    /** All slots for a provider (all states) — used by provider dashboard. */
    List<AvailabilitySlot> getSlotsByProvider(Long providerId);

    /** Only available (unbooked, unblocked) slots — used by patient slot picker. */
    List<AvailabilitySlot> getAvailableSlots(Long providerId, LocalDate date);

    /** Available slots across a date range — used by patient calendar view. */
    List<AvailabilitySlot> getAvailableSlotsInRange(Long providerId,
                                                     LocalDate startDate,
                                                     LocalDate endDate);

    // ── State transitions ──────────────────────────────────────────────────────

    /**
     * Books a slot atomically using optimistic locking.
     * If two patients try simultaneously, exactly one succeeds.
     *
     * @param slotId        the slot to book
     * @param appointmentId the appointment being created (stored on slot for reference)
     * @throws SlotAlreadyBookedException if slot is booked or lock collision occurred
     */
    AvailabilitySlot bookSlot(Long slotId, Long appointmentId);

    /**
     * Releases a booked slot back to AVAILABLE.
     * Called by appointment-service when patient cancels their appointment.
     */
    AvailabilitySlot releaseSlot(Long slotId);

    /**
     * Blocks a slot — provider marks it unavailable (personal time, leave).
     * Blocked slots disappear from patient search immediately.
     */
    AvailabilitySlot blockSlot(Long slotId);

    /** Unblocks a previously blocked slot, making it available again. */
    AvailabilitySlot unblockSlot(Long slotId);
}
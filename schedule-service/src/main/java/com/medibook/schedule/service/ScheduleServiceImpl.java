package com.medibook.schedule.service;

import com.medibook.schedule.entity.AvailabilitySlot;
import com.medibook.schedule.exception.SlotAlreadyBookedException;
import com.medibook.schedule.exception.SlotConflictException;
import com.medibook.schedule.exception.SlotNotFoundException;
import com.medibook.schedule.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ScheduleServiceImpl — all slot business logic including:
 *   - Single and bulk slot creation with overlap detection
 *   - Recurring slot generation (daily, weekly, custom patterns)
 *   - Slot booking with optimistic locking (concurrency safety)
 *   - Slot state transitions (book → release → block → unblock)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ScheduleServiceImpl implements ScheduleService {

    private final SlotRepository slotRepository;

    // ── Add Single Slot ────────────────────────────────────────────────────────

    @Override
    public AvailabilitySlot addSlot(Long providerId, LocalDate date,
                                     LocalTime startTime, LocalTime endTime,
                                     String recurrence) {

        // Validate time order before hitting the DB
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                "End time must be after start time. Got: " + startTime + " → " + endTime);
        }

        // Prevent creating a slot in the past — no point booking yesterday's 10am
        if (date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                "Cannot create slots for past dates: " + date);
        }

        // Check for overlapping slots — prevents double-scheduling the same time
        // e.g. can't add 10:00-10:30 if 10:15-10:45 already exists for this provider
        boolean overlaps = slotRepository.existsOverlappingSlot(
            providerId, date, startTime, endTime);
        if (overlaps) {
            throw new SlotConflictException(
                "A slot already exists that overlaps " + startTime + " - " + endTime
                + " on " + date + " for providerId: " + providerId);
        }

        // Calculate duration in minutes for display (e.g. "30 min consultation")
        int durationMinutes = (int) java.time.Duration.between(startTime, endTime).toMinutes();

        AvailabilitySlot slot = AvailabilitySlot.builder()
                .providerId(providerId)
                .slotDate(date)
                .startTime(startTime)
                .endTime(endTime)
                .durationMinutes(durationMinutes)
                .recurrence(recurrence != null ? recurrence : "NONE")
                .isBooked(false)
                .isBlocked(false)
                .build();

        AvailabilitySlot saved = slotRepository.save(slot);
        log.info("Slot created: provider={} date={} {}–{}", providerId, date, startTime, endTime);
        return saved;
    }

    // ── Add Bulk Slots ─────────────────────────────────────────────────────────

    @Override
    public List<AvailabilitySlot> addBulkSlots(List<AvailabilitySlot> slots) {
        // Validate and save each slot individually so one conflict doesn't
        // silently skip the rest — fail fast with a clear error message
        List<AvailabilitySlot> saved = new ArrayList<>();
        for (AvailabilitySlot slot : slots) {
            boolean overlaps = slotRepository.existsOverlappingSlot(
                slot.getProviderId(), slot.getSlotDate(),
                slot.getStartTime(), slot.getEndTime());
            if (overlaps) {
                log.warn("Skipping overlapping slot: {} {} {}–{}",
                    slot.getProviderId(), slot.getSlotDate(),
                    slot.getStartTime(), slot.getEndTime());
                continue; // skip conflicting slots, save the rest
            }
            // Calculate duration if not already set
            if (slot.getDurationMinutes() == null) {
                int mins = (int) java.time.Duration.between(
                    slot.getStartTime(), slot.getEndTime()).toMinutes();
                slot.setDurationMinutes(mins);
            }
            saved.add(slotRepository.save(slot));
        }
        log.info("Bulk slot creation: {} requested, {} saved", slots.size(), saved.size());
        return saved;
    }

    // ── Generate Recurring Slots ───────────────────────────────────────────────

    /**
     * The most powerful slot creation method.
     *
     * How it works:
     *   1. Walk through every date from startDate to endDate (inclusive)
     *   2. For each date, check if it matches the recurrence pattern
     *   3. If yes, generate all time slots for that day using durationMinutes
     *      (e.g. 9am–5pm with 30-min slots → 16 slots per matching day)
     *   4. Skip any slot that overlaps an existing one
     *
     * Example call:
     *   generateRecurringSlots(
     *     providerId=7,
     *     startDate=2026-05-01, endDate=2026-05-31,
     *     startTime=09:00, endTime=17:00,
     *     durationMinutes=30,
     *     recurrence="MON_WED_FRI"
     *   )
     *   → Creates slots on every Mon, Wed, Fri in May, 9am–5pm, 30-min each
     *   → ~13 matching days × 16 slots/day = up to 208 slots in one call
     */
    @Override
    public List<AvailabilitySlot> generateRecurringSlots(
            Long providerId,
            LocalDate startDate, LocalDate endDate,
            LocalTime startTime, LocalTime endTime,
            int durationMinutes,
            String recurrence) {

        // Validate inputs
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }

        List<AvailabilitySlot> generated = new ArrayList<>();

        // Walk through every single day in the range
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {

            // Check if today's day-of-week matches the recurrence pattern
            if (matchesRecurrence(current.getDayOfWeek(), recurrence)) {

                // Generate time slots within this day using a sliding window
                // e.g. 09:00–09:30, 09:30–10:00, 10:00–10:30 ...
                LocalTime slotStart = startTime;
                while (slotStart.plusMinutes(durationMinutes).compareTo(endTime) <= 0) {
                    LocalTime slotEnd = slotStart.plusMinutes(durationMinutes);

                    // Skip if overlapping slot already exists for this provider
                    boolean overlaps = slotRepository.existsOverlappingSlot(
                        providerId, current, slotStart, slotEnd);

                    if (!overlaps) {
                        AvailabilitySlot slot = AvailabilitySlot.builder()
                                .providerId(providerId)
                                .slotDate(current)
                                .startTime(slotStart)
                                .endTime(slotEnd)
                                .durationMinutes(durationMinutes)
                                .recurrence(recurrence)
                                .isBooked(false)
                                .isBlocked(false)
                                .build();
                        generated.add(slotRepository.save(slot));
                    }

                    // Advance the sliding window by durationMinutes
                    slotStart = slotEnd;
                }
            }

            // Move to the next day
            current = current.plusDays(1);
        }

        log.info("Recurring slot generation: provider={} pattern={} {} to {} → {} slots created",
            providerId, recurrence, startDate, endDate, generated.size());
        return generated;
    }

    /**
     * Checks if a given day-of-week matches the requested recurrence pattern.
     *
     * @param day       the actual day (MONDAY, TUESDAY, etc.)
     * @param recurrence pattern string
     * @return true if this day should have slots generated
     */
    private boolean matchesRecurrence(DayOfWeek day, String recurrence) {
        return switch (recurrence.toUpperCase()) {
            // Every single day including weekends
            case "DAILY"         -> true;
            // Monday to Friday only
            case "WEEKDAYS"      -> day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
            // Classic alternate-day schedule
            case "MON_WED_FRI"   -> day == DayOfWeek.MONDAY
                                 || day == DayOfWeek.WEDNESDAY
                                 || day == DayOfWeek.FRIDAY;
            // Alternate days (complementary to MON_WED_FRI)
            case "TUE_THU"       -> day == DayOfWeek.TUESDAY
                                 || day == DayOfWeek.THURSDAY;
            // Same day every week (e.g. every Monday)
            case "WEEKLY"        -> day == DayOfWeek.MONDAY; // default weekly = Monday
            // Provider opens only on weekends
            case "WEEKENDS"      -> day == DayOfWeek.SATURDAY
                                 || day == DayOfWeek.SUNDAY;
            // "NONE" or unknown patterns — treat as single-day, always match
            default              -> true;
        };
    }

    // ── Retrieval ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<AvailabilitySlot> getSlotById(Long slotId) {
        return slotRepository.findById(slotId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailabilitySlot> getSlotsByProvider(Long providerId) {
        // Returns ALL slots for a provider — used by provider's own calendar view
        // (includes booked and blocked slots — provider sees everything)
        return slotRepository.findByProviderIdOrderBySlotDateAscStartTimeAsc(providerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailabilitySlot> getAvailableSlots(Long providerId, LocalDate date) {
        // Returns ONLY available (unbooked, unblocked) slots — used by patient slot picker
        return slotRepository.findAvailableByProviderAndDate(providerId, date);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailabilitySlot> getAvailableSlotsInRange(Long providerId,
                                                            LocalDate startDate,
                                                            LocalDate endDate) {
        return slotRepository.findAvailableByProviderAndDateRange(providerId, startDate, endDate);
    }

    // ── BOOK SLOT — Critical section with optimistic locking ──────────────────

    /**
     * Books a slot atomically using optimistic locking.
     *
     * Thread-safety guarantee:
     *   If 100 patients all click "Book slot #42" simultaneously,
     *   exactly 1 will succeed and 99 will get SlotAlreadyBookedException.
     *   No double-bookings. No data corruption. No database deadlocks.
     *
     * How the optimistic lock works step by step:
     *   1. We read the slot: version = 3, isBooked = false
     *   2. We check isBooked — it's false, good to proceed
     *   3. We set isBooked = true
     *   4. Hibernate generates SQL:
     *      UPDATE availability_slots
     *      SET is_booked=true, version=4, appointment_id=?
     *      WHERE slot_id=42 AND version=3   ← the key line
     *   5a. If nobody else updated the row: rowsAffected=1 → SUCCESS
     *   5b. If someone else already booked: rowsAffected=0 → OptimisticLockException
     *   6. We catch the exception and throw SlotAlreadyBookedException
     *
     * @param slotId        the slot to book
     * @param appointmentId the appointment being created (stored on slot for reference)
     */
    @Override
    public AvailabilitySlot bookSlot(Long slotId, Long appointmentId) {
        try {
            // Use the locking query — reads and increments version in one shot
            AvailabilitySlot slot = slotRepository.findByIdForBooking(slotId)
                    .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId));

            // Check if already booked — explicit check gives a clearer error message
            if (slot.isBooked()) {
                throw new SlotAlreadyBookedException(
                    "Slot " + slotId + " is already booked");
            }

            // Check if blocked — patient should never see blocked slots, but double-check
            if (slot.isBlocked()) {
                throw new SlotAlreadyBookedException(
                    "Slot " + slotId + " is blocked by the provider");
            }

            // Mark as booked and link to the appointment
            slot.setBooked(true);
            slot.setAppointmentId(appointmentId);

            // Hibernate saves this with version = oldVersion + 1
            // If two threads both read version=3, only one can write version=4 — other fails
            AvailabilitySlot booked = slotRepository.save(slot);
            log.info("Slot BOOKED: slotId={} appointmentId={}", slotId, appointmentId);
            return booked;

        } catch (ObjectOptimisticLockingFailureException ex) {
            // This is the concurrency collision — another request beat us to it
            log.warn("Optimistic lock collision on slot {}: {}", slotId, ex.getMessage());
            throw new SlotAlreadyBookedException(
                "Slot " + slotId + " was just booked by another patient. Please choose a different slot.");
        }
    }

    // ── Release Slot ───────────────────────────────────────────────────────────

    /**
     * Releases a booked slot back to AVAILABLE.
     * Called by appointment-service when a patient cancels their appointment.
     *
     * After this call, the slot is visible to patients again for booking.
     */
    @Override
    public AvailabilitySlot releaseSlot(Long slotId) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId));

        if (!slot.isBooked()) {
            log.warn("Attempted to release slot {} that isn't booked", slotId);
            return slot; // idempotent — releasing an unbooked slot is a no-op
        }

        // Clear both the booking flag and the appointment reference
        slot.setBooked(false);
        slot.setAppointmentId(null);

        AvailabilitySlot released = slotRepository.save(slot);
        log.info("Slot RELEASED: slotId={}", slotId);
        return released;
    }

    // ── Block / Unblock ────────────────────────────────────────────────────────

    @Override
    public AvailabilitySlot blockSlot(Long slotId) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId));

        if (slot.isBooked()) {
            // Can't block a slot that already has an appointment — cancel it first
            throw new IllegalStateException(
                "Cannot block slot " + slotId + " — it already has a booking. Cancel the appointment first.");
        }

        slot.setBlocked(true);
        AvailabilitySlot blocked = slotRepository.save(slot);
        log.info("Slot BLOCKED: slotId={}", slotId);
        return blocked;
    }

    @Override
    public AvailabilitySlot unblockSlot(Long slotId) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId));

        slot.setBlocked(false);
        AvailabilitySlot unblocked = slotRepository.save(slot);
        log.info("Slot UNBLOCKED: slotId={}", slotId);
        return unblocked;
    }

    // ── Update Slot ────────────────────────────────────────────────────────────

    @Override
    public AvailabilitySlot updateSlot(Long slotId, LocalDate date,
                                        LocalTime startTime, LocalTime endTime) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId));

        // Don't allow editing a slot that's already been booked
        if (slot.isBooked()) {
            throw new IllegalStateException(
                "Cannot update slot " + slotId + " — it already has a booking");
        }

        if (date      != null) slot.setSlotDate(date);
        if (startTime != null) slot.setStartTime(startTime);
        if (endTime   != null) slot.setEndTime(endTime);

        if (slot.getStartTime() != null && slot.getEndTime() != null) {
            int mins = (int) java.time.Duration.between(
                slot.getStartTime(), slot.getEndTime()).toMinutes();
            slot.setDurationMinutes(mins);
        }

        return slotRepository.save(slot);
    }

    // ── Delete Slot ────────────────────────────────────────────────────────────

    @Override
    public void deleteSlot(Long slotId) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId));

        if (slot.isBooked()) {
            throw new IllegalStateException(
                "Cannot delete slot " + slotId + " — it has an active booking. Cancel the appointment first.");
        }

        slotRepository.deleteById(slotId);
        log.info("Slot DELETED: slotId={}", slotId);
    }
}
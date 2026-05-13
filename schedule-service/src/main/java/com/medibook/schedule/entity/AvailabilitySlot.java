package com.medibook.schedule.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * AvailabilitySlot — the core entity of this service.
 *
 * Represents one bookable time block in a provider's calendar.
 * Example: Dr. Sharma is available on 20-Apr-2026 from 10:00 to 10:30.

 * Problem: Two patients (A and B) both see slot #42 as AVAILABLE.
 *   Both click "Book" at the same time.
 *   Without locking, both succeed → double booking. DISASTER.
 *
 * Solution: @Version on the 'version' field.
 *   Hibernate adds "WHERE version = ?" to every UPDATE.
 *   When A books the slot, version goes from 0 → 1.
 *   When B's update arrives, it checks "WHERE version = 0" — fails!
 *   Hibernate throws OptimisticLockException → we catch it → "Slot already booked".
 *   Only ONE booking ever succeeds. Zero double-bookings.
 *
 * This is better than PESSIMISTIC locking (SELECT FOR UPDATE) because:
 *   - No DB row lock held during network round trips
 *   - Scales much better under high load
 *   - Read-heavy workloads (browsing slots) are never blocked
 */
@Entity
@Table(
    name = "availability_slots",
    indexes = {
        // Most common query: "show me all slots for provider X on date Y"
        @Index(name = "idx_slot_provider_date", columnList = "provider_id, slot_date"),
        // Used by patient browse: only show AVAILABLE, unblocked slots
        @Index(name = "idx_slot_status",        columnList = "is_booked, is_blocked"),
        // Used by cleanup scheduler: find all past unbooked slots
        @Index(name = "idx_slot_date",          columnList = "slot_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AvailabilitySlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slot_id")
    private Long slotId;

    /**
     * providerId references Provider in provider_db.
     * Stored as plain Long — no JPA foreign key across databases.
     */
    @NotNull(message = "Provider ID is required")
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /**
     * The date this slot falls on.
     * Combined with startTime/endTime gives the exact appointment window.
     */
    @NotNull(message = "Slot date is required")
    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @NotNull(message = "Start time is required")
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // Duration stored explicitly for display — avoids recalculating from start/end
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /**
     * isBooked = true after appointment-service successfully books this slot.
     * Checked in the optimistic lock flow — see bookSlot() in service.
     */
    @Column(name = "is_booked", nullable = false)
    @Builder.Default
    private boolean isBooked = false;

    /**
     * isBlocked = true when provider manually blocks this slot (e.g. lunch, leave).
     * Blocked slots are INVISIBLE to patients but visible to provider + admin.
     */
    @Column(name = "is_blocked", nullable = false)
    @Builder.Default
    private boolean isBlocked = false;

    /**
     * Recurrence pattern — how this slot was generated.
     * Values: "NONE", "DAILY", "WEEKLY", "MON_WED_FRI", "TUE_THU", etc.
     * Stored for display only — actual recurring slots are pre-generated as
     * individual rows (one row = one slot), not computed at query time.
     */
    @Column(name = "recurrence", length = 50)
    @Builder.Default
    private String recurrence = "NONE";

    /**
     * appointmentId is set by appointment-service after booking.
     * Stored here so we can quickly find which appointment owns this slot
     * without calling appointment-service.
     */
    @Column(name = "appointment_id")
    private Long appointmentId;

    /**
     * ─── OPTIMISTIC LOCKING VERSION FIELD ───
     * @Version tells Hibernate to:
     *   1. Include this column in every UPDATE: SET version = version + 1
     *   2. Add a WHERE clause:               WHERE version = <old value>
     *   3. Throw OptimisticLockException if rowsAffected == 0
     *      (meaning someone else updated the row between our read and write)
     *
     * Never set this field manually — Hibernate manages it automatically.
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
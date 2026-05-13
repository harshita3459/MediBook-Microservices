package com.medibook.schedule.repository;

import com.medibook.schedule.entity.AvailabilitySlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * SlotRepository — data access layer for AvailabilitySlot.
 *
 * Spring Data JPA auto-implements all methods at runtime.
 * Custom queries use JPQL (not raw SQL) so they are database-independent.
 */
@Repository
public interface SlotRepository extends JpaRepository<AvailabilitySlot, Long> {

    // ── Provider dashboard queries ─────────────────────────────────────────────

    /**
     * All slots for a provider, sorted chronologically.
     * Returns booked + blocked + available — used by provider's own calendar.
     */
    List<AvailabilitySlot> findByProviderIdOrderBySlotDateAscStartTimeAsc(
            Long providerId);

    /**
     * All slots for a provider on a specific date (all states).
     * Used by provider to see their full day schedule.
     */
    List<AvailabilitySlot> findByProviderIdAndSlotDateOrderByStartTimeAsc(
            Long providerId, LocalDate slotDate);

    // ── Patient-facing queries — only AVAILABLE slots ──────────────────────────

    /**
     * Returns slots a patient can book on a specific date:
     *   isBooked=false AND isBlocked=false
     * Ordered by startTime so the UI shows them top-to-bottom chronologically.
     */
    @Query("""
            SELECT s FROM AvailabilitySlot s
            WHERE s.providerId = :providerId
              AND s.slotDate   = :date
              AND s.isBooked   = false
              AND s.isBlocked  = false
            ORDER BY s.startTime ASC
            """)
    List<AvailabilitySlot> findAvailableByProviderAndDate(
            @Param("providerId") Long providerId,
            @Param("date")       LocalDate date);

    /**
     * Available slots across a date range — used by the patient's calendar
     * to highlight which days have open appointments.
     */
    @Query("""
            SELECT s FROM AvailabilitySlot s
            WHERE s.providerId = :providerId
              AND s.slotDate   BETWEEN :startDate AND :endDate
              AND s.isBooked   = false
              AND s.isBlocked  = false
            ORDER BY s.slotDate ASC, s.startTime ASC
            """)
    List<AvailabilitySlot> findAvailableByProviderAndDateRange(
            @Param("providerId") Long providerId,
            @Param("startDate")  LocalDate startDate,
            @Param("endDate")    LocalDate endDate);

    /**
     * Count of available slots on a date — used by the calendar view
     * to mark dates as "has availability" without loading all slot objects.
     */
    @Query("""
            SELECT COUNT(s) FROM AvailabilitySlot s
            WHERE s.providerId = :providerId
              AND s.slotDate   = :date
              AND s.isBooked   = false
              AND s.isBlocked  = false
            """)
    long countAvailableByProviderAndDate(
            @Param("providerId") Long providerId,
            @Param("date")       LocalDate date);

    // ── Conflict detection ─────────────────────────────────────────────────────

    /**
     * Returns true if any existing slot for this provider on this date
     * overlaps with the given time window.
     *
     * Overlap condition (standard interval overlap):
     *   existing.startTime < newEndTime  AND  existing.endTime > newStartTime
     *
     * Examples:
     *   Existing 10:00–10:30 — new 10:15–10:45 → overlaps (true)
     *   Existing 10:00–10:30 — new 10:30–11:00 → no overlap (false) — adjacent, not overlapping
     *   Existing 10:00–10:30 — new 09:00–10:00 → no overlap (false)
     */
    @Query("""
            SELECT COUNT(s) > 0 FROM AvailabilitySlot s
            WHERE s.providerId = :providerId
              AND s.slotDate   = :date
              AND s.startTime  < :endTime
              AND s.endTime    > :startTime
            """)
    boolean existsOverlappingSlot(
            @Param("providerId") Long providerId,
            @Param("date")       LocalDate date,
            @Param("startTime")  LocalTime startTime,
            @Param("endTime")    LocalTime endTime);

    // ── Booking — uses @Version optimistic lock on entity ─────────────────────

    /**
     * Finds a slot by ID for the booking operation.
     *
     * OPTIMISTIC_FORCE_INCREMENT means Hibernate will increment the @Version
     * field when this entity is read, even if no other field changes.
     * This ensures that concurrent booking attempts are detected immediately
     * at read time, not just at write time.
     *
     * In practice: if two threads both call this query at the same time,
     * the first to commit the version increment wins;
     * the second gets ObjectOptimisticLockingFailureException.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT s FROM AvailabilitySlot s WHERE s.slotId = :slotId")
    Optional<AvailabilitySlot> findByIdForBooking(@Param("slotId") Long slotId);

    // ── Cleanup scheduler queries ──────────────────────────────────────────────

    /**
     * All past unbooked slots — loaded before deletion for logging purposes.
     * "Past" = slotDate is strictly before today.
     * Booked and blocked slots are NOT included — those stay for records.
     */
    @Query("""
            SELECT s FROM AvailabilitySlot s
            WHERE s.slotDate < :today
              AND s.isBooked  = false
              AND s.isBlocked = false
            """)
    List<AvailabilitySlot> findExpiredUnbookedSlots(@Param("today") LocalDate today);

    /**
     * Bulk-delete expired unbooked slots in a single SQL DELETE statement.
     *
     * @Modifying tells Spring Data this is a DML query (INSERT/UPDATE/DELETE).
     *   Without it, Spring treats the query as a SELECT and throws an exception.
     * clearAutomatically = true clears the JPA first-level cache after the delete
     *   so subsequent reads don't return stale deleted entities.
     *
     * Returns the count of deleted rows (useful for logging).
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            DELETE FROM AvailabilitySlot s
            WHERE s.slotDate < :today
              AND s.isBooked  = false
              AND s.isBlocked = false
            """)
    int deleteExpiredUnbookedSlots(@Param("today") LocalDate today);

    // ── Analytics / reporting ──────────────────────────────────────────────────

    /**
     * All booked slots for a provider in a date range.
     * Used by payment-service earnings report and provider analytics dashboard.
     */
    @Query("""
            SELECT s FROM AvailabilitySlot s
            WHERE s.providerId = :providerId
              AND s.isBooked   = true
              AND s.slotDate   BETWEEN :startDate AND :endDate
            ORDER BY s.slotDate ASC, s.startTime ASC
            """)
    List<AvailabilitySlot> findBookedByProviderAndDateRange(
            @Param("providerId") Long providerId,
            @Param("startDate")  LocalDate startDate,
            @Param("endDate")    LocalDate endDate);
}
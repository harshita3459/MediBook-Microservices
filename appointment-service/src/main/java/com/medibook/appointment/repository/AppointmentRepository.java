package com.medibook.appointment.repository;

import com.medibook.appointment.entity.Appointment;
import com.medibook.appointment.entity.Appointment.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Patient's appointment history — sorted newest first */
    List<Appointment> findByPatientIdOrderByAppointmentDateDesc(Long patientId);

    /** Provider's full appointment list */
    List<Appointment> findByProviderIdOrderByAppointmentDateAscStartTimeAsc(Long providerId);

    /** Provider's schedule for a specific date — used for today's appointments view */
    List<Appointment> findByProviderIdAndAppointmentDateOrderByStartTimeAsc(
            Long providerId, LocalDate date);

    /**
     * Find appointment by slotId — only considers ACTIVE statuses.
     * CANCELLED / COMPLETED / NO_SHOW appointments do NOT block a slot from being re-booked.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.slotId = :slotId
              AND a.status IN ('SCHEDULED', 'RESCHEDULED')
            """)
    Optional<Appointment> findActiveBySlotId(@Param("slotId") Long slotId);

    /**
     * Check whether a patient already has an ACTIVE appointment at the exact
     * same date and start-time (regardless of provider).
     * Used to prevent double-booking across different providers.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.patientId = :patientId
              AND a.appointmentDate = :date
              AND a.startTime = :startTime
              AND a.status IN ('SCHEDULED', 'RESCHEDULED')
            """)
    Optional<Appointment> findActiveConflictForPatient(
            @Param("patientId") Long patientId,
            @Param("date") LocalDate date,
            @Param("startTime") java.time.LocalTime startTime);

    /** Patient's upcoming appointments (SCHEDULED status, future dates) */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.patientId = :patientId
              AND a.status = 'SCHEDULED'
              AND a.appointmentDate >= :today
            ORDER BY a.appointmentDate ASC, a.startTime ASC
            """)
    List<Appointment> findUpcomingByPatient(
            @Param("patientId") Long patientId,
            @Param("today") LocalDate today);

    /** All appointments by status — used by admin dashboard */
    List<Appointment> findByStatus(AppointmentStatus status);

    /** Count appointments for a provider — used in analytics */
    long countByProviderId(Long providerId);

    /** All SCHEDULED appointments on a past date — used by no-show detection job */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.status = 'SCHEDULED'
              AND a.appointmentDate < :today
            """)
    List<Appointment> findScheduledBeforeDate(@Param("today") LocalDate today);
}
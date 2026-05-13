package com.medibook.appointment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Appointment — core entity linking a patient to a provider's slot.
 *
 * This service is the orchestrator: on booking it calls
 * schedule-service (lock slot) → payment-service (create record) → notification-service (send confirmation).
 * On cancellation it reverses: payment-service (refund) → schedule-service (release slot) → notification-service (alert).
 *
 * Cross-service IDs (patientId, providerId, slotId) are stored as plain Longs.
 * No @ManyToOne joins — those entities live in separate databases.
 */
@Entity
@Table(
        name = "appointments",
        indexes = {
                @Index(name = "idx_appt_patient",  columnList = "patient_id"),
                @Index(name = "idx_appt_provider", columnList = "provider_id"),
                @Index(name = "idx_appt_slot",     columnList = "slot_id"),
                @Index(name = "idx_appt_status",   columnList = "status"),
                @Index(name = "idx_appt_date",     columnList = "appointment_date")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long appointmentId;

    /** References users.user_id in auth_db */
    @NotNull
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** References providers.provider_id in provider_db */
    @NotNull
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /** References availability_slots.slot_id in schedule_db */
    @NotNull
    @Column(name = "slot_id", nullable = false, unique = true)
    private Long slotId;

    @Column(name = "appointment_date", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private LocalDate appointmentDate;

    @Column(name = "start_time")
    @JsonFormat(pattern = "HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalTime startTime;

    @Column(name = "end_time")
    @JsonFormat(pattern = "HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalTime endTime;

    /**
     * Appointment status — drives the full lifecycle.
     * SCHEDULED → COMPLETED (provider marks done)
     * SCHEDULED → CANCELLED (patient or provider cancels)
     * SCHEDULED → NO_SHOW  (system marks if not completed within window)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    /** CONSULTATION or FOLLOW_UP or EMERGENCY */
    @Column(name = "service_type", length = 50)
    private String serviceType;

    /** IN_PERSON or TELECONSULTATION */
    @Column(name = "mode_of_consultation", length = 30)
    private String modeOfConsultation;

    /** Optional notes from patient when booking ("chest pain since 3 days") */
    @Column(name = "patient_notes", columnDefinition = "TEXT")
    private String patientNotes;

    /** Reason recorded when appointment is cancelled */
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime updatedAt;

    public enum AppointmentStatus {
        SCHEDULED, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED
    }
}
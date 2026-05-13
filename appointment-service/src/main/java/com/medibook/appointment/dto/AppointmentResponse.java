package com.medibook.appointment.dto;

import com.medibook.appointment.entity.Appointment;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Response DTO — what we return to clients. Never exposes the entity directly. */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AppointmentResponse {

    private Long   appointmentId;
    private Long   patientId;
    private Long   providerId;
    private Long   slotId;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private LocalDate appointmentDate;
    @JsonFormat(pattern = "HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalTime startTime;
    @JsonFormat(pattern = "HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalTime endTime;
    private String status;
    private String serviceType;
    private String modeOfConsultation;
    private String patientNotes;
    private String cancellationReason;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime updatedAt;

    /** Static factory — converts entity to response DTO in one place */
    public static AppointmentResponse from(Appointment a) {
        return AppointmentResponse.builder()
                .appointmentId(a.getAppointmentId())
                .patientId(a.getPatientId())
                .providerId(a.getProviderId())
                .slotId(a.getSlotId())
                .appointmentDate(a.getAppointmentDate())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .status(a.getStatus().name())
                .serviceType(a.getServiceType())
                .modeOfConsultation(a.getModeOfConsultation())
                .patientNotes(a.getPatientNotes())
                .cancellationReason(a.getCancellationReason())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
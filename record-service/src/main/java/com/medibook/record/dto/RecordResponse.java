package com.medibook.record.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medibook.record.entity.MedicalRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordResponse {

    private Long recordId;
    private Long appointmentId;
    private Long patientId;
    private Long providerId;
    private String diagnosis;
    private String prescription;
    private String notes;
    private String labResults;
    private String attachmentUrl;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private LocalDate followUpDate;

    private Boolean followUpNotified;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime updatedAt;

    public static RecordResponse from(MedicalRecord r) {
        return RecordResponse.builder()
                .recordId(r.getRecordId())
                .appointmentId(r.getAppointmentId())
                .patientId(r.getPatientId())
                .providerId(r.getProviderId())
                .diagnosis(r.getDiagnosis())
                .prescription(r.getPrescription())
                .notes(r.getNotes())
                .labResults(r.getLabResults())
                .attachmentUrl(r.getAttachmentUrl())
                .followUpDate(r.getFollowUpDate())
                .followUpNotified(r.getFollowUpNotified())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}

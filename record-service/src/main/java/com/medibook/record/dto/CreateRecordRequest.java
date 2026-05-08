package com.medibook.record.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRecordRequest {

    @NotNull(message = "Appointment ID is required")
    private Long appointmentId;

    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotNull(message = "Provider ID is required")
    private Long providerId;

    @NotBlank(message = "Diagnosis is required")
    @Size(max = 2000, message = "Diagnosis must be under 2000 characters")
    private String diagnosis;

    @Size(max = 5000, message = "Prescription must be under 5000 characters")
    private String prescription;

    @Pattern(regexp = "^(?s)(?!\\s*$).{3,5000}$",
            message = "Notes are required and must be between 3 and 5000 characters")
    private String notes;

    @Pattern(regexp = "^(?s)(?!\\s*$).{3,5000}$",
            message = "Lab results are required and must be between 3 and 5000 characters")
    private String labResults;

    @Size(max = 500, message = "Attachment URL must be under 500 characters")
    private String attachmentUrl;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private LocalDate followUpDate;
}

package com.medibook.record.dto;

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
public class UpdateRecordRequest {

    @Pattern(regexp = "^(?s)(?!\\s*$).{3,2000}$",
            message = "Diagnosis must be between 3 and 2000 characters")
    private String diagnosis;

    @Size(max = 5000, message = "Prescription must be under 5000 characters")
    private String prescription;

    @Pattern(regexp = "^(?s)(?!\\s*$).{3,5000}$",
            message = "Notes must be between 3 and 5000 characters")
    private String notes;

    @Pattern(regexp = "^(?s)(?!\\s*$).{3,5000}$",
            message = "Lab results must be between 3 and 5000 characters")
    private String labResults;

    @Size(max = 500, message = "Attachment URL must be under 500 characters")
    private String attachmentUrl;

    private LocalDate followUpDate;
}

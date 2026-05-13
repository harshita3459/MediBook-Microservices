package com.medibook.record.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Request body for PUT /api/v1/records/{id}/attach — attach document URL */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AttachDocumentRequest {

    @NotBlank(message = "Document URL is required")
    private String attachmentUrl;
}

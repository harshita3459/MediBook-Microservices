package com.medibook.payment.dto;

import lombok.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Invoice details for a completed appointment payment */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoiceResponse {
    private String invoiceNumber;      // PAY-{paymentId}-{appointmentId}
    private Long paymentId;
    private Long appointmentId;
    private Long patientId;
    private Long providerId;
    private Double amount;
    private String currency;
    private String mode;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime paidAt;
    private String message;
}

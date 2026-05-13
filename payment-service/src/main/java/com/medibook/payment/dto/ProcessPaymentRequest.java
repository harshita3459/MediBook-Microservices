package com.medibook.payment.dto;

import com.medibook.payment.entity.Payment.PaymentMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessPaymentRequest {

    @NotNull(message = "Appointment ID is required")
    private Long appointmentId;

    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotNull(message = "Provider ID is required")
    private Long providerId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Double amount;

    @NotNull(message = "Payment mode is required")
    private PaymentMode mode;

    @Size(max = 100, message = "Transaction ID must be under 100 characters")
    private String transactionId;

    @Pattern(regexp = "^(INR|USD|EUR|GBP)?$", message = "Currency must be INR, USD, EUR or GBP")
    private String currency = "INR";

    @Size(max = 1000, message = "Notes must be under 1000 characters")
    private String notes;
}

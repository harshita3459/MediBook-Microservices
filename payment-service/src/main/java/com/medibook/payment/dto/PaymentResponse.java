package com.medibook.payment.dto;

import com.medibook.payment.entity.Payment;
import com.medibook.payment.entity.Payment.PaymentMode;
import com.medibook.payment.entity.Payment.PaymentStatus;
import lombok.*;

import java.time.LocalDateTime;

/** Outbound DTO — returned by all payment endpoints */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentResponse {

    private Long paymentId;
    private Long appointmentId;
    private Long patientId;
    private Long providerId;
    private Double amount;
    private PaymentStatus status;
    private PaymentMode mode;
    private String transactionId;
    private String currency;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    private String notes;
    private LocalDateTime createdAt;

    public static PaymentResponse from(Payment p) {
        return PaymentResponse.builder()
                .paymentId(p.getPaymentId())
                .appointmentId(p.getAppointmentId())
                .patientId(p.getPatientId())
                .providerId(p.getProviderId())
                .amount(p.getAmount())
                .status(p.getStatus())
                .mode(p.getMode())
                .transactionId(p.getTransactionId())
                .currency(p.getCurrency())
                .paidAt(p.getPaidAt())
                .refundedAt(p.getRefundedAt())
                .notes(p.getNotes())
                .createdAt(p.getCreatedAt())
                .build();
    }
}

package com.medibook.payment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Payment — core entity linking a financial transaction to an appointment.
 * One Payment per appointment (unique constraint on appointmentId).
 * Status lifecycle: PENDING -> PAID / FAILED / CASH; PAID -> REFUNDED
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_pay_appointment", columnList = "appointment_id"),
    @Index(name = "idx_pay_patient",     columnList = "patient_id"),
    @Index(name = "idx_pay_provider",    columnList = "provider_id"),
    @Index(name = "idx_pay_status",      columnList = "status"),
    @Index(name = "idx_pay_paid_at",     columnList = "paid_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    /** References appointments.appointment_id in appointment_db */
    @NotNull
    @Column(name = "appointment_id", nullable = false, unique = true)
    private Long appointmentId;

    /** References users.user_id in auth_db */
    @NotNull
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** References providers.provider_id in provider_db */
    @NotNull
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private PaymentMode mode;

    /** External transaction ID from Razorpay / Stripe / UPI */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "currency", length = 10)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "paid_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime paidAt;

    @Column(name = "refunded_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime refundedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime updatedAt;

    public enum PaymentStatus {
        PENDING,    // Created, awaiting gateway confirmation
        PAID,       // Gateway confirmed
        FAILED,     // Gateway rejected or timed out
        REFUNDED,   // Refund processed after cancellation
        CASH        // Pay-at-clinic
    }

    public enum PaymentMode {
        CARD,        // Debit/Credit card
        UPI,         // GPay, PhonePe, Paytm
        WALLET,      // MediBook internal wallet
        CASH,        // Pay at clinic
        NET_BANKING  // Online bank transfer
    }
}

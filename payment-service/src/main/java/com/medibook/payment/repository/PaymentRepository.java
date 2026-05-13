package com.medibook.payment.repository;

import com.medibook.payment.entity.Payment;
import com.medibook.payment.entity.Payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByAppointmentId(Long appointmentId);

    List<Payment> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<Payment> findByProviderIdOrderByCreatedAtDesc(Long providerId);

    List<Payment> findByStatus(PaymentStatus status);

    Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findByPaidAtBetween(LocalDateTime from, LocalDateTime to);

    /** Total amount collected by a provider (PAID + CASH only) */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.providerId = :providerId AND p.status IN ('PAID', 'CASH')")
    Double sumCollectedByProvider(@Param("providerId") Long providerId);

    /** Total refunded for a patient */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.patientId = :patientId AND p.status = 'REFUNDED'")
    Double sumRefundedByPatient(@Param("patientId") Long patientId);

    /** Provider revenue breakdown within a date range */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.providerId = :providerId AND p.status IN ('PAID', 'CASH') " +
           "AND p.paidAt BETWEEN :from AND :to")
    Double sumProviderEarningsBetween(@Param("providerId") Long providerId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    /** All payments for admin dashboard */
    List<Payment> findAllByOrderByCreatedAtDesc();

    long countByProviderId(Long providerId);
}

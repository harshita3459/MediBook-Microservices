package com.medibook.payment.service;

import com.medibook.payment.dto.EarningsSummary;
import com.medibook.payment.dto.InvoiceResponse;
import com.medibook.payment.dto.PaymentResponse;
import com.medibook.payment.dto.ProcessPaymentRequest;
import com.medibook.payment.entity.Payment;
import com.medibook.payment.entity.Payment.PaymentMode;
import com.medibook.payment.entity.Payment.PaymentStatus;
import com.medibook.payment.exception.InvalidPaymentStatusException;
import com.medibook.payment.exception.PaymentAlreadyExistsException;
import com.medibook.payment.exception.PaymentNotFoundException;
import com.medibook.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;

    @Value("${services.notification-url}")
    private String notificationUrl;

    @Override
    @CacheEvict(cacheNames = {
            "payments.byAppointment", "payments.byPatient", "payments.history",
            "payments.status", "payments.all", "payments.invoice",
            "payments.revenue", "payments.earnings"
    }, allEntries = true)
    public PaymentResponse processPayment(ProcessPaymentRequest req) {
        log.info("Processing payment: appointmentId={} mode={} amount={}",
                req.getAppointmentId(), req.getMode(), req.getAmount());

        if (paymentRepository.findByAppointmentId(req.getAppointmentId()).isPresent()) {
            throw new PaymentAlreadyExistsException(
                    "Payment already exists for appointmentId: " + req.getAppointmentId());
        }

        Payment.PaymentBuilder builder = Payment.builder()
                .appointmentId(req.getAppointmentId())
                .patientId(req.getPatientId())
                .providerId(req.getProviderId())
                .amount(req.getAmount())
                .mode(req.getMode())
                .transactionId(req.getTransactionId())
                .currency(req.getCurrency() != null ? req.getCurrency() : "INR")
                .notes(req.getNotes());

        if (req.getMode() == PaymentMode.CASH) {
            builder.status(PaymentStatus.CASH);
        } else {
            builder.status(PaymentStatus.PAID).paidAt(LocalDateTime.now());
        }

        Payment saved = paymentRepository.save(builder.build());
        log.info("Payment created: id={} status={}", saved.getPaymentId(), saved.getStatus());
        sendNotificationSafely("PAYMENT_RECEIPT", saved);
        return PaymentResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "payments.byAppointment", key = "#appointmentId")
    public PaymentResponse getPaymentByAppointment(Long appointmentId) {
        return PaymentResponse.from(findByAppointmentOrThrow(appointmentId));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "payments.byPatient", key = "#patientId")
    public List<PaymentResponse> getPaymentsByPatient(Long patientId) {
        return paymentRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream().map(PaymentResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "payments.history", key = "#patientId")
    public List<PaymentResponse> getPaymentHistory(Long patientId) {
        return getPaymentsByPatient(patientId);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "payments.status", key = "#paymentId")
    public String getPaymentStatus(Long paymentId) {
        return findOrThrow(paymentId).getStatus().name();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "payments.all", key = "'all'")
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(PaymentResponse::from).toList();
    }

    @Override
    @CacheEvict(cacheNames = {
            "payments.byAppointment", "payments.byPatient", "payments.history",
            "payments.status", "payments.all", "payments.invoice",
            "payments.revenue", "payments.earnings"
    }, allEntries = true)
    public PaymentResponse refundPayment(Long paymentId, String reason) {
        Payment payment = findOrThrow(paymentId);
        log.info("Processing refund: paymentId={} status={}", paymentId, payment.getStatus());

        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.CASH) {
            throw new InvalidPaymentStatusException(
                    "Cannot refund payment in status: " + payment.getStatus()
                            + ". Only PAID or CASH payments are eligible for refund.");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        if (reason != null) {
            payment.setNotes((payment.getNotes() != null ? payment.getNotes() + " | " : "")
                    + "Refund reason: " + reason);
        }

        Payment refunded = paymentRepository.save(payment);
        log.info("Refund completed: paymentId={}", paymentId);
        sendNotificationSafely("PAYMENT_REFUNDED", refunded);
        return PaymentResponse.from(refunded);
    }

    @Override
    @CacheEvict(cacheNames = {
            "payments.byAppointment", "payments.byPatient", "payments.history",
            "payments.status", "payments.all", "payments.invoice",
            "payments.revenue", "payments.earnings"
    }, allEntries = true)
    public PaymentResponse updatePaymentStatus(Long paymentId, String status) {
        Payment payment = findOrThrow(paymentId);
        PaymentStatus newStatus = PaymentStatus.valueOf(status.toUpperCase());
        payment.setStatus(newStatus);
        if (newStatus == PaymentStatus.PAID && payment.getPaidAt() == null) {
            payment.setPaidAt(LocalDateTime.now());
        }
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "payments.invoice", key = "#paymentId")
    public InvoiceResponse generateInvoice(Long paymentId) {
        Payment p = findOrThrow(paymentId);

        if (p.getStatus() != PaymentStatus.PAID && p.getStatus() != PaymentStatus.CASH) {
            throw new InvalidPaymentStatusException(
                    "Invoice can only be generated for PAID or CASH payments. Current status: " + p.getStatus());
        }

        return InvoiceResponse.builder()
                .invoiceNumber("INV-" + p.getPaymentId() + "-" + p.getAppointmentId())
                .paymentId(p.getPaymentId())
                .appointmentId(p.getAppointmentId())
                .patientId(p.getPatientId())
                .providerId(p.getProviderId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .mode(p.getMode().name())
                .status(p.getStatus().name())
                .paidAt(p.getPaidAt())
                .message("Invoice generated successfully by MediBook")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "payments.revenue", key = "#providerId")
    public Double getTotalRevenue(Long providerId) {
        Double total = paymentRepository.sumCollectedByProvider(providerId);
        return total != null ? total : 0.0;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "payments.earnings", key = "#providerId")
    public EarningsSummary getEarningsSummary(Long providerId) {
        double collected = getTotalRevenue(providerId);
        long totalTxns = paymentRepository.countByProviderId(providerId);

        List<Payment> all = paymentRepository.findByProviderIdOrderByCreatedAtDesc(providerId);
        double refunded = all.stream()
                .filter(p -> p.getStatus() == PaymentStatus.REFUNDED)
                .mapToDouble(Payment::getAmount).sum();
        double pending = all.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .mapToDouble(Payment::getAmount).sum();

        return EarningsSummary.builder()
                .providerId(providerId)
                .totalCollected(collected)
                .totalRefunded(refunded)
                .pendingAmount(pending)
                .totalTransactions(totalTxns)
                .build();
    }

    private Payment findOrThrow(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private Payment findByAppointmentOrThrow(Long appointmentId) {
        return paymentRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "No payment found for appointmentId: " + appointmentId));
    }

    private void sendNotificationSafely(String eventType, Payment payment) {
        try {
            Map<String, Object> payload = Map.of(
                    "eventType", eventType,
                    "paymentId", payment.getPaymentId(),
                    "appointmentId", payment.getAppointmentId(),
                    "patientId", payment.getPatientId(),
                    "amount", payment.getAmount()
            );
            restTemplate.postForEntity(notificationUrl + "/payment-event", payload, String.class);
        } catch (Exception ex) {
            log.warn("Notification send failed (non-blocking): eventType={} error={}", eventType, ex.getMessage());
        }
    }
}

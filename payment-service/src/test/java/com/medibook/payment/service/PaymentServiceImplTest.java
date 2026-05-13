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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for PaymentServiceImpl.
 *
 * Critical scenarios covered:
 *   - processPayment: CASH vs online modes set correct status
 *   - Duplicate payment for same appointment → 409
 *   - refundPayment: only PAID/CASH are eligible; reason is appended to notes
 *   - generateInvoice: only PAID/CASH produce an invoice
 *   - getEarningsSummary: correct breakdown of collected / refunded / pending
 *   - updatePaymentStatus: sets paidAt only if transitioning to PAID for first time
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl Tests")
class PaymentServiceImplTest {

    @Mock PaymentRepository paymentRepository;
    @Mock RestTemplate      restTemplate;

    @InjectMocks PaymentServiceImpl paymentService;

    @BeforeEach
    void injectUrls() {
        ReflectionTestUtils.setField(paymentService, "notificationUrl",
                "http://notification-svc/internal");
    }

    // ── Fixture helpers ───────────────────────────────────────────────────

    private Payment buildPayment(Long id, Long appointmentId,
                                  PaymentStatus status, PaymentMode mode, Double amount) {
        return Payment.builder()
                .paymentId(id)
                .appointmentId(appointmentId)
                .patientId(1L)
                .providerId(10L)
                .amount(amount)
                .status(status)
                .mode(mode)
                .currency("INR")
                .build();
    }

    private ProcessPaymentRequest buildRequest(Long appointmentId,
                                                PaymentMode mode, Double amount) {
        return ProcessPaymentRequest.builder()
                .appointmentId(appointmentId)
                .patientId(1L)
                .providerId(10L)
                .amount(amount)
                .mode(mode)
                .currency("INR")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // processPayment
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processPayment()")
    class ProcessPaymentTests {

        @Test
        @DisplayName("✅ online payment (UPI) → status PAID with paidAt set")
        void process_upiPayment_statusIsPaidWithTimestamp() {
            ProcessPaymentRequest req = buildRequest(100L, PaymentMode.UPI, 500.0);
            given(paymentRepository.findByAppointmentId(100L)).willReturn(Optional.empty());

            Payment saved = buildPayment(1L, 100L, PaymentStatus.PAID, PaymentMode.UPI, 500.0);
            saved.setPaidAt(LocalDateTime.now());
            given(paymentRepository.save(any())).willReturn(saved);

            PaymentResponse result = paymentService.processPayment(req);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(result.getPaidAt()).isNotNull();
            then(paymentRepository).should().save(argThat(p ->
                    p.getStatus() == PaymentStatus.PAID && p.getPaidAt() != null));
        }

        @Test
        @DisplayName("✅ CASH payment → status CASH (not PAID), paidAt NOT set")
        void process_cashPayment_statusIsCashNoPaidAt() {
            ProcessPaymentRequest req = buildRequest(101L, PaymentMode.CASH, 300.0);
            given(paymentRepository.findByAppointmentId(101L)).willReturn(Optional.empty());

            Payment saved = buildPayment(2L, 101L, PaymentStatus.CASH, PaymentMode.CASH, 300.0);
            given(paymentRepository.save(any())).willReturn(saved);

            PaymentResponse result = paymentService.processPayment(req);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.CASH);
            then(paymentRepository).should().save(argThat(p ->
                    p.getStatus() == PaymentStatus.CASH && p.getPaidAt() == null));
        }

        @Test
        @DisplayName("✅ currency defaults to INR when not supplied")
        void process_noCurrency_defaultsToINR() {
            ProcessPaymentRequest req = ProcessPaymentRequest.builder()
                    .appointmentId(102L).patientId(1L).providerId(10L)
                    .amount(200.0).mode(PaymentMode.CARD)
                    .currency(null) // not provided
                    .build();

            given(paymentRepository.findByAppointmentId(102L)).willReturn(Optional.empty());
            Payment saved = buildPayment(3L, 102L, PaymentStatus.PAID, PaymentMode.CARD, 200.0);
            given(paymentRepository.save(any())).willReturn(saved);

            paymentService.processPayment(req);

            then(paymentRepository).should().save(argThat(p -> "INR".equals(p.getCurrency())));
        }

        @Test
        @DisplayName("❌ duplicate payment for same appointmentId → PaymentAlreadyExistsException")
        void process_duplicate_throwsConflict() {
            given(paymentRepository.findByAppointmentId(200L))
                    .willReturn(Optional.of(buildPayment(99L, 200L, PaymentStatus.PAID,
                            PaymentMode.UPI, 400.0)));

            assertThatThrownBy(() ->
                    paymentService.processPayment(buildRequest(200L, PaymentMode.UPI, 400.0)))
                    .isInstanceOf(PaymentAlreadyExistsException.class)
                    .hasMessageContaining("200");

            then(paymentRepository).should(never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // refundPayment
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("refundPayment()")
    class RefundPaymentTests {

        @Test
        @DisplayName("✅ PAID payment can be refunded → status REFUNDED, refundedAt set")
        void refund_paidPayment_statusRefunded() {
            Payment payment = buildPayment(1L, 100L, PaymentStatus.PAID, PaymentMode.UPI, 500.0);
            given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
            given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            PaymentResponse result = paymentService.refundPayment(1L, "Appointment cancelled");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            then(paymentRepository).should().save(argThat(p ->
                    p.getStatus() == PaymentStatus.REFUNDED && p.getRefundedAt() != null));
        }

        @Test
        @DisplayName("✅ CASH payment can also be refunded")
        void refund_cashPayment_statusRefunded() {
            Payment payment = buildPayment(2L, 101L, PaymentStatus.CASH, PaymentMode.CASH, 300.0);
            given(paymentRepository.findById(2L)).willReturn(Optional.of(payment));
            given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            PaymentResponse result = paymentService.refundPayment(2L, "Doctor unavailable");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("✅ refund reason is appended to existing notes")
        void refund_withReason_appendedToNotes() {
            Payment payment = buildPayment(3L, 102L, PaymentStatus.PAID, PaymentMode.CARD, 600.0);
            payment.setNotes("Original note");
            given(paymentRepository.findById(3L)).willReturn(Optional.of(payment));
            given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            paymentService.refundPayment(3L, "Patient requested");

            then(paymentRepository).should().save(argThat(p ->
                    p.getNotes() != null
                    && p.getNotes().contains("Original note")
                    && p.getNotes().contains("Patient requested")));
        }

        @Test
        @DisplayName("❌ PENDING payment cannot be refunded")
        void refund_pendingPayment_throwsInvalidStatus() {
            Payment payment = buildPayment(4L, 103L, PaymentStatus.PENDING, PaymentMode.UPI, 200.0);
            given(paymentRepository.findById(4L)).willReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment(4L, "reason"))
                    .isInstanceOf(InvalidPaymentStatusException.class)
                    .hasMessageContaining("PENDING")
                    .hasMessageContaining("PAID or CASH");
        }

        @Test
        @DisplayName("❌ FAILED payment cannot be refunded")
        void refund_failedPayment_throwsInvalidStatus() {
            Payment payment = buildPayment(5L, 104L, PaymentStatus.FAILED, PaymentMode.UPI, 100.0);
            given(paymentRepository.findById(5L)).willReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment(5L, "reason"))
                    .isInstanceOf(InvalidPaymentStatusException.class);
        }

        @Test
        @DisplayName("❌ already REFUNDED payment cannot be refunded again")
        void refund_alreadyRefunded_throwsInvalidStatus() {
            Payment payment = buildPayment(6L, 105L, PaymentStatus.REFUNDED, PaymentMode.UPI, 250.0);
            given(paymentRepository.findById(6L)).willReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment(6L, "reason"))
                    .isInstanceOf(InvalidPaymentStatusException.class);
        }

        @Test
        @DisplayName("❌ refunding non-existent payment throws PaymentNotFoundException")
        void refund_notFound_throwsNotFoundException() {
            given(paymentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.refundPayment(999L, "reason"))
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // generateInvoice
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateInvoice()")
    class GenerateInvoiceTests {

        @Test
        @DisplayName("✅ PAID payment generates invoice with correct invoice number")
        void invoice_paidPayment_generatesInvoice() {
            Payment payment = buildPayment(7L, 200L, PaymentStatus.PAID, PaymentMode.UPI, 750.0);
            payment.setPaidAt(LocalDateTime.now());
            given(paymentRepository.findById(7L)).willReturn(Optional.of(payment));

            InvoiceResponse invoice = paymentService.generateInvoice(7L);

            assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-7-200");
            assertThat(invoice.getPaymentId()).isEqualTo(7L);
            assertThat(invoice.getAppointmentId()).isEqualTo(200L);
            assertThat(invoice.getAmount()).isEqualTo(750.0);
        }

        @Test
        @DisplayName("✅ CASH payment also generates invoice")
        void invoice_cashPayment_generatesInvoice() {
            Payment payment = buildPayment(8L, 201L, PaymentStatus.CASH, PaymentMode.CASH, 400.0);
            given(paymentRepository.findById(8L)).willReturn(Optional.of(payment));

            InvoiceResponse invoice = paymentService.generateInvoice(8L);

            assertThat(invoice.getInvoiceNumber()).startsWith("INV-8-");
            assertThat(invoice.getMode()).isEqualTo("CASH");
        }

        @Test
        @DisplayName("❌ PENDING payment cannot generate invoice")
        void invoice_pendingPayment_throwsInvalidStatus() {
            Payment payment = buildPayment(9L, 202L, PaymentStatus.PENDING, PaymentMode.UPI, 500.0);
            given(paymentRepository.findById(9L)).willReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.generateInvoice(9L))
                    .isInstanceOf(InvalidPaymentStatusException.class)
                    .hasMessageContaining("PAID or CASH");
        }

        @Test
        @DisplayName("❌ REFUNDED payment cannot generate new invoice")
        void invoice_refundedPayment_throwsInvalidStatus() {
            Payment payment = buildPayment(10L, 203L, PaymentStatus.REFUNDED, PaymentMode.CARD, 600.0);
            given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.generateInvoice(10L))
                    .isInstanceOf(InvalidPaymentStatusException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getEarningsSummary
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getEarningsSummary()")
    class EarningsSummaryTests {

        @Test
        @DisplayName("✅ summary correctly sums collected, refunded and pending amounts")
        void earnings_correctBreakdown() {
            Long providerId = 10L;

            given(paymentRepository.sumCollectedByProvider(providerId)).willReturn(1500.0);
            given(paymentRepository.countByProviderId(providerId)).willReturn(5L);

            // Mix: 1 REFUNDED + 1 PENDING + 2 PAID + 1 CASH
            Payment paid1    = buildPayment(1L, 1L, PaymentStatus.PAID,     PaymentMode.UPI,  500.0);
            Payment paid2    = buildPayment(2L, 2L, PaymentStatus.PAID,     PaymentMode.CARD, 600.0);
            Payment cash     = buildPayment(3L, 3L, PaymentStatus.CASH,     PaymentMode.CASH, 400.0);
            Payment refunded = buildPayment(4L, 4L, PaymentStatus.REFUNDED, PaymentMode.UPI,  300.0);
            Payment pending  = buildPayment(5L, 5L, PaymentStatus.PENDING,  PaymentMode.UPI,  250.0);

            given(paymentRepository.findByProviderIdOrderByCreatedAtDesc(providerId))
                    .willReturn(List.of(paid1, paid2, cash, refunded, pending));

            EarningsSummary summary = paymentService.getEarningsSummary(providerId);

            assertThat(summary.getProviderId()).isEqualTo(providerId);
            assertThat(summary.getTotalCollected()).isEqualTo(1500.0); // from sumCollected mock
            assertThat(summary.getTotalRefunded()).isEqualTo(300.0);   // only REFUNDED
            assertThat(summary.getPendingAmount()).isEqualTo(250.0);   // only PENDING
            assertThat(summary.getTotalTransactions()).isEqualTo(5L);
        }

        @Test
        @DisplayName("✅ returns zero summary for provider with no payments")
        void earnings_noPayments_returnsZeros() {
            given(paymentRepository.sumCollectedByProvider(99L)).willReturn(null); // DB returns null on no rows
            given(paymentRepository.countByProviderId(99L)).willReturn(0L);
            given(paymentRepository.findByProviderIdOrderByCreatedAtDesc(99L)).willReturn(List.of());

            EarningsSummary summary = paymentService.getEarningsSummary(99L);

            assertThat(summary.getTotalCollected()).isEqualTo(0.0);
            assertThat(summary.getTotalRefunded()).isEqualTo(0.0);
            assertThat(summary.getPendingAmount()).isEqualTo(0.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // updatePaymentStatus
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updatePaymentStatus()")
    class UpdateStatusTests {

        @Test
        @DisplayName("✅ transitioning to PAID sets paidAt if not already set")
        void updateStatus_toPaid_setsPaidAt() {
            Payment payment = buildPayment(1L, 100L, PaymentStatus.PENDING, PaymentMode.UPI, 400.0);
            given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
            given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            PaymentResponse result = paymentService.updatePaymentStatus(1L, "PAID");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
            then(paymentRepository).should().save(argThat(p ->
                    p.getStatus() == PaymentStatus.PAID && p.getPaidAt() != null));
        }

        @Test
        @DisplayName("✅ transitioning to FAILED does not set paidAt")
        void updateStatus_toFailed_noPaidAt() {
            Payment payment = buildPayment(2L, 101L, PaymentStatus.PENDING, PaymentMode.CARD, 200.0);
            given(paymentRepository.findById(2L)).willReturn(Optional.of(payment));
            given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            PaymentResponse result = paymentService.updatePaymentStatus(2L, "FAILED");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            then(paymentRepository).should().save(argThat(p -> p.getPaidAt() == null));
        }

        @Test
        @DisplayName("❌ invalid status string throws IllegalArgumentException")
        void updateStatus_invalidString_throwsException() {
            Payment payment = buildPayment(3L, 102L, PaymentStatus.PENDING, PaymentMode.UPI, 100.0);
            given(paymentRepository.findById(3L)).willReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.updatePaymentStatus(3L, "UNKNOWN_STATUS"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getPaymentByAppointment / getTotalRevenue / getPaymentStatus
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Read operations")
    class ReadTests {

        @Test
        @DisplayName("✅ getPaymentByAppointment returns correct DTO")
        void getByAppointment_exists_returnsResponse() {
            Payment payment = buildPayment(1L, 300L, PaymentStatus.PAID, PaymentMode.UPI, 500.0);
            given(paymentRepository.findByAppointmentId(300L)).willReturn(Optional.of(payment));

            PaymentResponse result = paymentService.getPaymentByAppointment(300L);

            assertThat(result.getAppointmentId()).isEqualTo(300L);
            assertThat(result.getAmount()).isEqualTo(500.0);
        }

        @Test
        @DisplayName("❌ getPaymentByAppointment for missing appointmentId throws PaymentNotFoundException")
        void getByAppointment_missing_throws() {
            given(paymentRepository.findByAppointmentId(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentByAppointment(999L))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("✅ getTotalRevenue returns 0.0 when repository returns null")
        void getTotalRevenue_nullFromRepo_returnsZero() {
            given(paymentRepository.sumCollectedByProvider(5L)).willReturn(null);

            assertThat(paymentService.getTotalRevenue(5L)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("✅ getPaymentStatus returns status name string")
        void getPaymentStatus_returnsStatusName() {
            Payment payment = buildPayment(1L, 100L, PaymentStatus.PAID, PaymentMode.UPI, 500.0);
            given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

            String status = paymentService.getPaymentStatus(1L);

            assertThat(status).isEqualTo("PAID");
        }

        @Test
        @DisplayName("✅ getPaymentsByPatient returns all payments ordered by date")
        void getByPatient_returnsList() {
            given(paymentRepository.findByPatientIdOrderByCreatedAtDesc(1L))
                    .willReturn(List.of(
                            buildPayment(1L, 100L, PaymentStatus.PAID, PaymentMode.UPI,  500.0),
                            buildPayment(2L, 101L, PaymentStatus.CASH, PaymentMode.CASH, 300.0)));

            List<PaymentResponse> result = paymentService.getPaymentsByPatient(1L);

            assertThat(result).hasSize(2);
        }
    }
}
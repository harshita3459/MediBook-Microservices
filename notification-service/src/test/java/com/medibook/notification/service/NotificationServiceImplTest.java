package com.medibook.notification.service;

import com.medibook.notification.consumer.AppointmentEventConsumer;
import com.medibook.notification.consumer.PaymentEventConsumer;
import com.medibook.notification.entity.Notification;
import com.medibook.notification.entity.Notification.NotificationChannel;
import com.medibook.notification.entity.Notification.NotificationType;
import com.medibook.notification.dto.NotificationResponse;
import com.medibook.notification.exception.NotificationNotFoundException;
import com.medibook.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for NotificationServiceImpl and RabbitMQ consumers.
 *
 * NotificationServiceImpl:
 *   - send(): always persists to DB; email/SMS dispatched per channel
 *   - sendBulk(): one failure does not stop others
 *   - sendEmail(): respects emailEnabled flag
 *   - markAsRead() / markAllAsRead()
 *   - unread count calculation
 *
 * AppointmentEventConsumer:
 *   - handleBookingConfirmed: sends notifications to patient AND provider
 *   - handleCancellation: both parties notified
 *   - handleReminder: 1H vs 24H labels differ
 *   - handleFollowUp: correct type used
 *
 * PaymentEventConsumer:
 *   - PAYMENT_SUCCESS event → PAYMENT_SUCCESS notification type
 *   - PAYMENT_REFUNDED event → PAYMENT_REFUNDED notification type
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService + Consumer Tests")
class NotificationServiceImplTest {

    // ── Service under test ────────────────────────────────────────────────

    @Mock NotificationRepository notificationRepository;
    @Mock JavaMailSender         mailSender;

    @InjectMocks NotificationServiceImpl notificationService;

    @BeforeEach
    void injectProperties() {
        ReflectionTestUtils.setField(notificationService, "fromEmail",    "noreply@medibook.com");
        ReflectionTestUtils.setField(notificationService, "emailEnabled", true);
        ReflectionTestUtils.setField(notificationService, "smsEnabled",   false);
    }

    // ── Fixture helpers ───────────────────────────────────────────────────

    private Notification buildNotification(Long id, Long recipientId,
                                            NotificationType type, boolean read) {
        return Notification.builder()
                .notificationId(id)
                .recipientId(recipientId)
                .type(type)
                .title("Test Title")
                .message("Test Message")
                .channel(NotificationChannel.APP)
                .isRead(read)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NotificationServiceImpl — send()
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("send()")
    class SendTests {

        @Test
        @DisplayName("✅ always persists notification to DB regardless of channel")
        void send_appChannel_persistsToDb() {
            Notification saved = buildNotification(1L, 5L, NotificationType.BOOKING_CONFIRMED, false);
            given(notificationRepository.save(any())).willReturn(saved);

            NotificationResponse result = notificationService.send(
                    5L, NotificationType.BOOKING_CONFIRMED,
                    "Booking Confirmed", "Your slot at 10:00 is confirmed.",
                    NotificationChannel.APP, 100L, "APPOINTMENT");

            assertThat(result.getNotificationId()).isEqualTo(1L);
            assertThat(result.getRecipientId()).isEqualTo(5L);
            assertThat(result.getType()).isEqualTo("BOOKING_CONFIRMED");
            assertThat(result.isRead()).isFalse();
            then(notificationRepository).should().save(argThat(n ->
                    n.getRecipientId().equals(5L)
                    && n.getType() == NotificationType.BOOKING_CONFIRMED
                    && !n.isRead()
            ));
        }

        @Test
        @DisplayName("✅ new notification is always persisted as isRead=false")
        void send_alwaysUnread() {
            Notification saved = buildNotification(2L, 7L, NotificationType.REMINDER_24H, false);
            given(notificationRepository.save(any())).willReturn(saved);

            NotificationResponse result = notificationService.send(
                    7L, NotificationType.REMINDER_24H,
                    "Reminder", "Your appointment is tomorrow.",
                    NotificationChannel.EMAIL, null, null);

            assertThat(result.isRead()).isFalse();
            then(notificationRepository).should().save(argThat(n -> !n.isRead()));
        }

        @Test
        @DisplayName("✅ relatedId and relatedType are stored on the entity")
        void send_relatedFields_persisted() {
            Notification saved = buildNotification(3L, 1L, NotificationType.PAYMENT_SUCCESS, false);
            saved.setRelatedId(99L);
            saved.setRelatedType("PAYMENT");
            given(notificationRepository.save(any())).willReturn(saved);

            NotificationResponse result = notificationService.send(
                    1L, NotificationType.PAYMENT_SUCCESS,
                    "Payment OK", "₹500 received.",
                    NotificationChannel.ALL, 99L, "PAYMENT");

            assertThat(result.getRelatedId()).isEqualTo(99L);
            assertThat(result.getRelatedType()).isEqualTo("PAYMENT");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // sendBulk()
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("sendBulk()")
    class SendBulkTests {

        @Test
        @DisplayName("✅ sends to all recipients — one save per recipient")
        void sendBulk_allSucceed_savesAll() {
            Notification saved = buildNotification(1L, 1L, NotificationType.BULK_MESSAGE, false);
            given(notificationRepository.save(any())).willReturn(saved);

            notificationService.sendBulk(
                    List.of(1L, 2L, 3L),
                    "Maintenance", "System down at 2am.",
                    NotificationChannel.APP);

            then(notificationRepository).should(times(3)).save(any());
        }

        @Test
        @DisplayName("✅ one recipient failure does NOT stop the rest (fault isolation)")
        void sendBulk_oneFailure_continuesForOthers() {
            // First call succeeds, second throws, third succeeds
            Notification n = buildNotification(1L, 1L, NotificationType.BULK_MESSAGE, false);
            given(notificationRepository.save(any()))
                    .willReturn(n)
                    .willThrow(new RuntimeException("DB error"))
                    .willReturn(n);

            assertThatCode(() -> notificationService.sendBulk(
                    List.of(1L, 2L, 3L),
                    "Alert", "Important message",
                    NotificationChannel.EMAIL))
                    .doesNotThrowAnyException();

            // All 3 attempted, even though middle one failed
            then(notificationRepository).should(times(3)).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // sendEmail()
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("sendEmail()")
    class SendEmailTests {

        @Test
        @DisplayName("✅ sends email when emailEnabled=true")
        void sendEmail_enabled_callsMailSender() {
            willDoNothing().given(mailSender).send(any(SimpleMailMessage.class));

            notificationService.sendEmail("patient@test.com", "Booking OK", "Your slot is confirmed.");

            then(mailSender).should().send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("✅ skips email when emailEnabled=false — no mail sent")
        void sendEmail_disabled_skips() {
            ReflectionTestUtils.setField(notificationService, "emailEnabled", false);

            notificationService.sendEmail("patient@test.com", "Subject", "Body");

            then(mailSender).should(never()).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("✅ mail sender failure is swallowed — does not propagate")
        void sendEmail_senderThrows_doesNotPropagate() {
            willThrow(new RuntimeException("SMTP server down"))
                    .given(mailSender).send(any(SimpleMailMessage.class));

            assertThatCode(() ->
                    notificationService.sendEmail("fail@test.com", "Subject", "Body"))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // markAsRead() / markAllAsRead()
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("markAsRead() / markAllAsRead()")
    class MarkReadTests {

        @Test
        @DisplayName("✅ markAsRead sets isRead=true and saves")
        void markAsRead_setsReadTrue() {
            Notification notification = buildNotification(1L, 1L, NotificationType.BOOKING_CONFIRMED, false);
            given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));
            given(notificationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            NotificationResponse result = notificationService.markAsRead(1L);

            assertThat(result.isRead()).isTrue();
            then(notificationRepository).should().save(argThat(Notification::isRead));
        }

        @Test
        @DisplayName("❌ markAsRead for non-existent ID throws NotificationNotFoundException")
        void markAsRead_notFound_throwsException() {
            given(notificationRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markAsRead(999L))
                    .isInstanceOf(NotificationNotFoundException.class);
        }

        @Test
        @DisplayName("✅ markAllAsRead returns count of notifications updated")
        void markAllAsRead_returnsCount() {
            given(notificationRepository.markAllAsRead(1L)).willReturn(5);

            int updated = notificationService.markAllAsRead(1L);

            assertThat(updated).isEqualTo(5);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getUnreadCount / getByRecipient / getUnreadByRecipient
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Read operations")
    class ReadTests {

        @Test
        @DisplayName("✅ getUnreadCount returns count from repository")
        void getUnreadCount_returnsCount() {
            given(notificationRepository.countByRecipientIdAndIsReadFalse(1L)).willReturn(7L);

            assertThat(notificationService.getUnreadCount(1L)).isEqualTo(7L);
        }

        @Test
        @DisplayName("✅ getByRecipient returns all notifications newest-first")
        void getByRecipient_returnsList() {
            given(notificationRepository.findByRecipientIdOrderBySentAtDesc(1L))
                    .willReturn(List.of(
                            buildNotification(1L, 1L, NotificationType.BOOKING_CONFIRMED, false),
                            buildNotification(2L, 1L, NotificationType.REMINDER_24H, true)));

            List<NotificationResponse> result = notificationService.getByRecipient(1L);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("✅ getUnreadByRecipient returns only unread notifications")
        void getUnreadByRecipient_returnsUnreadOnly() {
            given(notificationRepository.findByRecipientIdAndIsReadFalseOrderBySentAtDesc(1L))
                    .willReturn(List.of(
                            buildNotification(1L, 1L, NotificationType.BOOKING_CONFIRMED, false)));

            List<NotificationResponse> result = notificationService.getUnreadByRecipient(1L);

            assertThat(result).hasSize(1)
                    .allMatch(n -> !n.isRead());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // deleteNotification
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteNotification()")
    class DeleteTests {

        @Test
        @DisplayName("✅ deletes existing notification")
        void delete_existing_callsDeleteById() {
            given(notificationRepository.existsById(1L)).willReturn(true);

            notificationService.deleteNotification(1L);

            then(notificationRepository).should().deleteById(1L);
        }

        @Test
        @DisplayName("❌ deleting non-existent notification throws NotificationNotFoundException")
        void delete_notFound_throwsException() {
            given(notificationRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() -> notificationService.deleteNotification(999L))
                    .isInstanceOf(NotificationNotFoundException.class);
        }
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// AppointmentEventConsumer Unit Tests
// ══════════════════════════════════════════════════════════════════════════════

@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentEventConsumer Tests")
class AppointmentEventConsumerTest {

    @Mock NotificationService notificationService;
    @InjectMocks AppointmentEventConsumer consumer;

    private Map<String, Object> bookingPayload(Long appointmentId, Long patientId, Long providerId) {
        Map<String, Object> p = new HashMap<>();
        p.put("appointmentId",  appointmentId);
        p.put("patientId",      patientId);
        p.put("providerId",     providerId);
        p.put("appointmentDate", "2026-05-10");
        p.put("startTime",       "10:00:00");
        p.put("providerName",    "Dr. Sharma");
        return p;
    }

    @Nested
    @DisplayName("handleBookingConfirmed()")
    class BookingConfirmedTests {

        @Test
        @DisplayName("✅ sends notification to BOTH patient and provider")
        void booking_notifiesBothParties() {
            consumer.handleBookingConfirmed(bookingPayload(1L, 10L, 20L));

            // Patient notification
            then(notificationService).should().send(
                    eq(10L), eq(NotificationType.BOOKING_CONFIRMED),
                    anyString(), anyString(), any(), eq(1L), eq("APPOINTMENT"));

            // Provider notification
            then(notificationService).should().send(
                    eq(20L), eq(NotificationType.BOOKING_CONFIRMED),
                    anyString(), anyString(), any(), eq(1L), eq("APPOINTMENT"));
        }

        @Test
        @DisplayName("✅ providerName=null in payload doesn't crash — fallback text used")
        void booking_nullProviderName_usesDefaultText() {
            Map<String, Object> payload = bookingPayload(2L, 10L, 20L);
            payload.remove("providerName");

            assertThatCode(() -> consumer.handleBookingConfirmed(payload))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("❌ service.send() throwing causes RuntimeException to be re-thrown (for retry)")
        void booking_serviceFails_throwsForRetry() {
            willThrow(new RuntimeException("Notification DB down"))
                    .given(notificationService).send(anyLong(), any(), any(), any(), any(), any(), any());

            assertThatThrownBy(() -> consumer.handleBookingConfirmed(bookingPayload(3L, 10L, 20L)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to process booking event");
        }
    }

    @Nested
    @DisplayName("handleCancellation()")
    class CancellationTests {

        @Test
        @DisplayName("✅ cancellation notifies patient and provider with correct type")
        void cancellation_notifiesBothParties() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("appointmentId", 5L);
            payload.put("patientId",     10L);
            payload.put("providerId",    20L);
            payload.put("reason",        "Patient request");

            consumer.handleCancellation(payload);

            then(notificationService).should(times(2)).send(
                    anyLong(), eq(NotificationType.BOOKING_CANCELLED),
                    anyString(), anyString(), any(), eq(5L), eq("APPOINTMENT"));
        }

        @Test
        @DisplayName("✅ reason=null in payload is handled gracefully")
        void cancellation_nullReason_noException() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("appointmentId", 6L);
            payload.put("patientId",     10L);
            payload.put("providerId",    20L);
            payload.put("reason",        null);

            assertThatCode(() -> consumer.handleCancellation(payload))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("handleReminder()")
    class ReminderTests {

        @Test
        @DisplayName("✅ 1H reminderType maps to REMINDER_1H notification type")
        void reminder_1H_usesCorrectType() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("patientId",      10L);
            payload.put("appointmentId",  7L);
            payload.put("reminderType",   "1H");
            payload.put("appointmentDate", "2026-05-10");
            payload.put("startTime",       "15:00:00");

            consumer.handleReminder(payload);

            then(notificationService).should().send(
                    eq(10L), eq(NotificationType.REMINDER_1H),
                    anyString(), contains("1 hour"), any(), eq(7L), eq("APPOINTMENT"));
        }

        @Test
        @DisplayName("✅ 24H reminderType maps to REMINDER_24H notification type")
        void reminder_24H_usesCorrectType() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("patientId",      10L);
            payload.put("appointmentId",  8L);
            payload.put("reminderType",   "24H");
            payload.put("appointmentDate", "2026-05-11");
            payload.put("startTime",       "09:00:00");

            consumer.handleReminder(payload);

            then(notificationService).should().send(
                    eq(10L), eq(NotificationType.REMINDER_24H),
                    anyString(), contains("24 hours"), any(), eq(8L), eq("APPOINTMENT"));
        }
    }

    @Nested
    @DisplayName("handleFollowUp()")
    class FollowUpTests {

        @Test
        @DisplayName("✅ follow-up event sends FOLLOWUP_REMINDER to patient")
        void followUp_sendsCorrectType() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("patientId", 10L);
            payload.put("recordId",  50L);
            payload.put("message",   "Your follow-up is today.");

            consumer.handleFollowUp(payload);

            then(notificationService).should().send(
                    eq(10L), eq(NotificationType.FOLLOWUP_REMINDER),
                    anyString(), eq("Your follow-up is today."),
                    any(), eq(50L), eq("MEDICAL_RECORD"));
        }

        @Test
        @DisplayName("✅ null message falls back to default text")
        void followUp_nullMessage_usesDefault() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("patientId", 10L);
            payload.put("recordId",  50L);
            payload.put("message",   null);

            assertThatCode(() -> consumer.handleFollowUp(payload))
                    .doesNotThrowAnyException();
        }
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// PaymentEventConsumer Unit Tests
// ══════════════════════════════════════════════════════════════════════════════

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventConsumer Tests")
class PaymentEventConsumerTest {

    @Mock NotificationService notificationService;
    @InjectMocks PaymentEventConsumer consumer;

    @Nested
    @DisplayName("handlePaymentEvent()")
    class PaymentEventTests {

        @Test
        @DisplayName("✅ PAYMENT_SUCCESS event → PAYMENT_SUCCESS notification type")
        void paymentSuccess_usesCorrectType() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("patientId",   10L);
            payload.put("paymentId",   99L);
            payload.put("eventType",   "PAYMENT_SUCCESS");
            payload.put("amount",      "500.0");

            consumer.handlePaymentEvent(payload);

            then(notificationService).should().send(
                    eq(10L), eq(NotificationType.PAYMENT_SUCCESS),
                    anyString(), contains("500"), any(), eq(99L), eq("PAYMENT"));
        }

        @Test
        @DisplayName("✅ PAYMENT_REFUNDED event → PAYMENT_REFUNDED notification type")
        void paymentRefunded_usesCorrectType() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("patientId",   10L);
            payload.put("paymentId",   100L);
            payload.put("eventType",   "PAYMENT_REFUNDED");
            payload.put("amount",      "300.0");

            consumer.handlePaymentEvent(payload);

            then(notificationService).should().send(
                    eq(10L), eq(NotificationType.PAYMENT_REFUNDED),
                    anyString(), contains("300"), any(), eq(100L), eq("PAYMENT"));
        }

        @Test
        @DisplayName("✅ unknown eventType is silently ignored — no notification sent")
        void unknownEventType_noNotificationSent() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("patientId",  10L);
            payload.put("paymentId",  101L);
            payload.put("eventType",  "SOMETHING_ELSE");
            payload.put("amount",     "100.0");

            assertThatCode(() -> consumer.handlePaymentEvent(payload))
                    .doesNotThrowAnyException();

            then(notificationService).should(never()).send(
                    anyLong(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("❌ notification service failure causes re-throw for RabbitMQ retry")
        void paymentEvent_serviceThrows_rethrowsForRetry() {
            willThrow(new RuntimeException("DB timeout"))
                    .given(notificationService).send(anyLong(), any(), any(), any(), any(), any(), any());

            Map<String, Object> payload = new HashMap<>();
            payload.put("patientId",  10L);
            payload.put("paymentId",  102L);
            payload.put("eventType",  "PAYMENT_SUCCESS");
            payload.put("amount",     "200.0");

            assertThatThrownBy(() -> consumer.handlePaymentEvent(payload))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to process payment event");
        }
    }
}
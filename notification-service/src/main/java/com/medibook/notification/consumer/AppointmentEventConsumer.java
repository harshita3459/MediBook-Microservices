package com.medibook.notification.consumer;

import com.medibook.notification.config.RabbitMQConfig;
import com.medibook.notification.entity.Notification.NotificationChannel;
import com.medibook.notification.entity.Notification.NotificationType;
import com.medibook.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AppointmentEventConsumer — listens to RabbitMQ queues and creates notifications.
 *
 * HOW IT WORKS:
 * ─────────────
 * 1. appointment-service publishes a message to RabbitMQ exchange with a routing key
 *    e.g. "appointment.booking.confirmed"
 * 2. RabbitMQ routes it to medibook.booking.queue (based on binding in RabbitMQConfig)
 * 3. @RabbitListener picks it up and this method runs
 * 4. We create in-app notification, send email, etc.
 *
 * The message payload arrives as a Map<String, Object> (JSON deserialized by Jackson).
 *
 * RETRY BEHAVIOR (configured in application.yml):
 *   - If this method throws any exception, Spring retries up to 3 times
 *   - After 3 failures, the message goes to the DLQ (dead letter queue)
 *   - Check DLQ in RabbitMQ Management UI: http://localhost:15672
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentEventConsumer {

    private final NotificationService notificationService;

    /**
     * Listens to booking confirmation events.
     * Triggered when appointment-service publishes to "appointment.booking.#"
     *
     * Expected payload keys:
     *   patientId, providerId, appointmentId, appointmentDate, startTime,
     *   patientName, providerName
     */
    @RabbitListener(queues = RabbitMQConfig.BOOKING_QUEUE)
    public void handleBookingConfirmed(Map<String, Object> payload) {
        log.info("RabbitMQ received: BOOKING event — payload={}", payload);

        try {
            Long patientId      = getLong(payload, "patientId");
            Long providerId     = getLong(payload, "providerId");
            Long appointmentId  = getLong(payload, "appointmentId");
            String date         = getString(payload, "appointmentDate");
            String time         = getString(payload, "startTime");
            String providerName = getString(payload, "providerName");

            String title   = "Appointment Confirmed";
            String message = String.format(
                "Your appointment with %s on %s at %s has been confirmed. Appointment ID: %d",
                providerName != null ? providerName : "your doctor", date, time, appointmentId);

            // Save in-app notification for patient
            notificationService.send(patientId, NotificationType.BOOKING_CONFIRMED,
                title, message, NotificationChannel.ALL, appointmentId, "APPOINTMENT");

            // Also notify the provider
            String providerMsg = String.format(
                "New appointment booked. Appointment ID: %d on %s at %s", appointmentId, date, time);
            notificationService.send(providerId, NotificationType.BOOKING_CONFIRMED,
                "New Appointment", providerMsg, NotificationChannel.APP, appointmentId, "APPOINTMENT");

            log.info("Booking confirmation notifications sent for appointmentId={}", appointmentId);

        } catch (Exception ex) {
            log.error("Failed to process booking event: {}", ex.getMessage(), ex);
            // Re-throw so Spring retry mechanism catches it
            throw new RuntimeException("Failed to process booking event", ex);
        }
    }

    /**
     * Listens to cancellation events.
     * Triggered when appointment-service publishes "appointment.cancelled"
     */
    @RabbitListener(queues = RabbitMQConfig.CANCELLATION_QUEUE)
    public void handleCancellation(Map<String, Object> payload) {
        log.info("RabbitMQ received: CANCELLATION event — payload={}", payload);

        try {
            Long patientId     = getLong(payload, "patientId");
            Long providerId    = getLong(payload, "providerId");
            Long appointmentId = getLong(payload, "appointmentId");
            String reason      = getString(payload, "reason");

            String patientMsg = String.format(
                "Your appointment (ID: %d) has been cancelled. %s",
                appointmentId, reason != null ? "Reason: " + reason : "");

            notificationService.send(patientId, NotificationType.BOOKING_CANCELLED,
                "Appointment Cancelled", patientMsg, NotificationChannel.ALL,
                appointmentId, "APPOINTMENT");

            notificationService.send(providerId, NotificationType.BOOKING_CANCELLED,
                "Appointment Cancelled", "An appointment has been cancelled. ID: " + appointmentId,
                NotificationChannel.APP, appointmentId, "APPOINTMENT");

        } catch (Exception ex) {
            log.error("Failed to process cancellation event: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to process cancellation event", ex);
        }
    }

    /**
     * Listens to reminder events.
     * Triggered by schedule-service's Quartz job 24h and 1h before appointments.
     * Routing key: "appointment.reminder.24h" or "appointment.reminder.1h"
     */
    @RabbitListener(queues = RabbitMQConfig.REMINDER_QUEUE)
    public void handleReminder(Map<String, Object> payload) {
        log.info("RabbitMQ received: REMINDER event — payload={}", payload);

        try {
            Long patientId     = getLong(payload, "patientId");
            Long appointmentId = getLong(payload, "appointmentId");
            String reminderType = getString(payload, "reminderType"); // "24H" or "1H"
            String date        = getString(payload, "appointmentDate");
            String time        = getString(payload, "startTime");

            String timeLabel = "1H".equals(reminderType) ? "1 hour" : "24 hours";
            String message   = String.format(
                "Reminder: Your appointment is in %s, on %s at %s. Appointment ID: %d",
                timeLabel, date, time, appointmentId);

            NotificationType type = "1H".equals(reminderType)
                    ? NotificationType.REMINDER_1H
                    : NotificationType.REMINDER_24H;

            notificationService.send(patientId, type,
                "Appointment Reminder", message, NotificationChannel.ALL,
                appointmentId, "APPOINTMENT");

        } catch (Exception ex) {
            log.error("Failed to process reminder event: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to process reminder event", ex);
        }
    }

    /**
     * Listens to follow-up reminder events.
     * Triggered by record-service's Quartz job on the follow-up date.
     */
    @RabbitListener(queues = RabbitMQConfig.FOLLOWUP_QUEUE)
    public void handleFollowUp(Map<String, Object> payload) {
        log.info("RabbitMQ received: FOLLOW-UP event — payload={}", payload);

        try {
            Long patientId  = getLong(payload, "patientId");
            Long recordId   = getLong(payload, "recordId");
            String message  = getString(payload, "message");

            notificationService.send(patientId, NotificationType.FOLLOWUP_REMINDER,
                "Follow-Up Reminder", message != null ? message : "Your follow-up date is today.",
                NotificationChannel.ALL, recordId, "MEDICAL_RECORD");

        } catch (Exception ex) {
            log.error("Failed to process follow-up event: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to process follow-up event", ex);
        }
    }

    // ── Safe type helpers — payload values come as Object from JSON deserialization ──

    private Long getLong(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val == null) return null;
        return val instanceof Number ? ((Number) val).longValue() : Long.valueOf(val.toString());
    }

    private String getString(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        return val != null ? val.toString() : null;
    }
}
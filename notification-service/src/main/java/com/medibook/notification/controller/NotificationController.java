package com.medibook.notification.controller;

import com.medibook.notification.dto.NotificationResponse;
import com.medibook.notification.entity.Notification;
import com.medibook.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * NotificationResource — REST API for notifications.
 * Base URL: /api/v1/notifications   Port: 8087
 *
 * Most notifications are created automatically via RabbitMQ consumers.
 * This REST API allows:
 *   - Patients/providers to fetch and manage their notifications
 *   - Admin to send manual/bulk notifications
 *   - appointment-service to post events directly (HTTP fallback if RabbitMQ is down)
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "In-app, email, and SMS notification management")
public class NotificationController {

    private final NotificationService notificationService;

    // ── POST /api/v1/notifications ─────────────────────────────────────────────
    /** Send a notification directly via HTTP — used for manual sends */
    @PostMapping
    @Operation(summary = "Send a notification (admin or internal)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<NotificationResponse> send(@RequestBody SendBody body) {
        NotificationResponse response = notificationService.send(
            body.recipientId(), body.type(), body.title(), body.message(),
            body.channel() != null ? body.channel() : Notification.NotificationChannel.APP,
            body.relatedId(), body.relatedType());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── POST /api/v1/notifications/appointment-event ──────────────────────────
    /**
     * HTTP fallback endpoint — appointment-service calls this directly
     * if RabbitMQ is down. In normal operation, events arrive via RabbitMQ consumers.
     */
    @PostMapping("/appointment-event")
    @Operation(summary = "Process appointment event (internal — HTTP fallback)")
    public ResponseEntity<Map<String, String>> handleAppointmentEvent(
            @RequestBody Map<String, Object> payload) {
        log.info("HTTP fallback: appointment event received: {}", payload.get("eventType"));

        String eventType   = (String) payload.get("eventType");
        Object rawPatient  = payload.get("patientId");
        Long   patientId   = rawPatient != null ? ((Number) rawPatient).longValue() : null;
        Object rawAppt     = payload.get("appointmentId");
        Long   appointmentId = rawAppt != null ? ((Number) rawAppt).longValue() : null;

        if (patientId != null) {
            String title   = getTitleForEvent(eventType);
            String message = String.format("Appointment %s. ID: %d", eventType, appointmentId);
            Notification.NotificationType type = getTypeForEvent(eventType);

            notificationService.send(patientId, type, title, message,
                Notification.NotificationChannel.APP, appointmentId, "APPOINTMENT");
        }

        return ResponseEntity.ok(Map.of("message", "Event processed"));
    }

    @PostMapping("/payment-event")
    @Operation(summary = "Process payment event (internal - HTTP fallback)")
    public ResponseEntity<Map<String, String>> handlePaymentEvent(
            @RequestBody Map<String, Object> payload) {
        log.info("HTTP fallback: payment event received: {}", payload.get("eventType"));

        String eventType = (String) payload.get("eventType");
        Object rawPatient = payload.get("patientId");
        Long patientId = rawPatient != null ? ((Number) rawPatient).longValue() : null;
        Object rawPayment = payload.get("paymentId");
        Long paymentId = rawPayment != null ? ((Number) rawPayment).longValue() : null;

        if (patientId != null) {
            String title = getTitleForPaymentEvent(eventType);
            String message = String.format("Payment %s. ID: %d", eventType, paymentId);
            Notification.NotificationType type = getTypeForPaymentEvent(eventType);

            notificationService.send(patientId, type, title, message,
                Notification.NotificationChannel.APP, paymentId, "PAYMENT");
        }

        return ResponseEntity.ok(Map.of("message", "Payment event processed"));
    }

    // ── POST /api/v1/notifications/bulk ───────────────────────────────────────
    /** Admin sends a notification to multiple users at once */
    @PostMapping("/bulk")
    @Operation(summary = "Send bulk notification (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> sendBulk(@RequestBody BulkBody body) {
        notificationService.sendBulk(
            body.recipientIds(), body.title(), body.message(),
            body.channel() != null ? body.channel() : Notification.NotificationChannel.APP);
        return ResponseEntity.ok(Map.of(
            "message",    "Bulk notification sent",
            "recipients", body.recipientIds().size()
        ));
    }

    // ── GET /api/v1/notifications/recipient/{id} ──────────────────────────────
    /** Fetch all notifications for a user — for the notification center */
    @GetMapping("/recipient/{recipientId}")
    @Operation(summary = "Get all notifications for a user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<NotificationResponse>> getByRecipient(
            @PathVariable Long recipientId) {
        return ResponseEntity.ok(notificationService.getByRecipient(recipientId));
    }

    // ── GET /api/v1/notifications/recipient/{id}/unread ───────────────────────
    /** Only unread notifications — for the notification panel */
    @GetMapping("/recipient/{recipientId}/unread")
    @Operation(summary = "Get unread notifications for a user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<NotificationResponse>> getUnread(
            @PathVariable Long recipientId) {
        return ResponseEntity.ok(notificationService.getUnreadByRecipient(recipientId));
    }

    // ── GET /api/v1/notifications/recipient/{id}/count ───────────────────────
    /** Returns the unread count — used for the red badge on the bell icon */
    @GetMapping("/recipient/{recipientId}/count")
    @Operation(summary = "Get unread notification count (bell badge)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @PathVariable Long recipientId) {
        return ResponseEntity.ok(Map.of("unreadCount",
                notificationService.getUnreadCount(recipientId)));
    }

    // ── PUT /api/v1/notifications/{id}/read ───────────────────────────────────
    @PutMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    // ── PUT /api/v1/notifications/recipient/{id}/read-all ─────────────────────
    @PutMapping("/recipient/{recipientId}/read-all")
    @Operation(summary = "Mark all notifications as read for a user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            @PathVariable Long recipientId) {
        int updated = notificationService.markAllAsRead(recipientId);
        return ResponseEntity.ok(Map.of("message", "Marked " + updated + " notifications as read"));
    }

    // ── DELETE /api/v1/notifications/{id} ─────────────────────────────────────
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a notification")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.ok(Map.of("message", "Notification deleted"));
    }

    // ── GET /api/v1/notifications (admin) ─────────────────────────────────────
    @GetMapping
    @Operation(summary = "Get all notifications — admin only")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<NotificationResponse>> getAll() {
        return ResponseEntity.ok(notificationService.getAll());
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String getTitleForEvent(String eventType) {
        if (eventType == null) return "Notification";
        return switch (eventType) {
            case "BOOKING_CONFIRMED"     -> "Appointment Confirmed";
            case "APPOINTMENT_CANCELLED" -> "Appointment Cancelled";
            case "APPOINTMENT_COMPLETED" -> "Appointment Completed";
            case "APPOINTMENT_RESCHEDULED" -> "Appointment Rescheduled";
            default -> "Appointment Update";
        };
    }

    private Notification.NotificationType getTypeForEvent(String eventType) {
        if (eventType == null) return Notification.NotificationType.BOOKING_CONFIRMED;
        return switch (eventType) {
            case "BOOKING_CONFIRMED"     -> Notification.NotificationType.BOOKING_CONFIRMED;
            case "APPOINTMENT_CANCELLED" -> Notification.NotificationType.BOOKING_CANCELLED;
            case "APPOINTMENT_COMPLETED" -> Notification.NotificationType.APPOINTMENT_COMPLETED;
            case "APPOINTMENT_RESCHEDULED" -> Notification.NotificationType.BOOKING_RESCHEDULED;
            default -> Notification.NotificationType.BOOKING_CONFIRMED;
        };
    }

    private String getTitleForPaymentEvent(String eventType) {
        if (eventType == null) return "Payment Update";
        return switch (eventType) {
            case "PAYMENT_RECEIPT" -> "Payment Successful";
            case "PAYMENT_REFUNDED" -> "Payment Refunded";
            default -> "Payment Update";
        };
    }

    private Notification.NotificationType getTypeForPaymentEvent(String eventType) {
        if (eventType == null) return Notification.NotificationType.PAYMENT_SUCCESS;
        return switch (eventType) {
            case "PAYMENT_RECEIPT" -> Notification.NotificationType.PAYMENT_SUCCESS;
            case "PAYMENT_REFUNDED" -> Notification.NotificationType.PAYMENT_REFUNDED;
            default -> Notification.NotificationType.PAYMENT_SUCCESS;
        };
    }

    // ── Request body records ──────────────────────────────────────────────────

    record SendBody(
        @NotNull Long recipientId,
        @NotNull Notification.NotificationType type,
        @NotBlank String title,
        @NotBlank String message,
        Notification.NotificationChannel channel,
        Long relatedId,
        String relatedType
    ) {}

    record BulkBody(
        @NotNull List<Long> recipientIds,
        @NotBlank String title,
        @NotBlank String message,
        Notification.NotificationChannel channel
    ) {}
}

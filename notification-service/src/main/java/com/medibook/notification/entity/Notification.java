package com.medibook.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Notification entity — one row per notification sent (or attempted).
 *
 * Every booking confirmation, reminder, cancellation alert, payment receipt,
 * and follow-up reminder is stored here so patients and providers can see
 * their notification history in the app.
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        // Most common query: fetch all notifications for a user
        @Index(name = "idx_notif_recipient",       columnList = "recipient_id"),
        // For unread badge count: fetch only unread for a user
        @Index(name = "idx_notif_recipient_unread", columnList = "recipient_id, is_read"),
        // Admin queries by type
        @Index(name = "idx_notif_type",             columnList = "type")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    /** userId from auth_db — who receives this notification */
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Channel used to deliver: APP, EMAIL, SMS, or ALL */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    @Builder.Default
    private NotificationChannel channel = NotificationChannel.APP;

    /** Foreign key to the related entity — e.g. appointmentId, paymentId */
    @Column(name = "related_id")
    private Long relatedId;

    /** Type of the related entity — e.g. "APPOINTMENT", "PAYMENT" */
    @Column(name = "related_type", length = 30)
    private String relatedType;

    /** false = new/unread, true = user has seen this notification */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;

    public enum NotificationType {
        BOOKING_CONFIRMED,
        BOOKING_CANCELLED,
        BOOKING_RESCHEDULED,
        REMINDER_24H,
        REMINDER_1H,
        PAYMENT_SUCCESS,
        PAYMENT_REFUNDED,
        FOLLOWUP_REMINDER,
        APPOINTMENT_COMPLETED,
        BULK_MESSAGE
    }

    public enum NotificationChannel {
        APP,    // in-app only
        EMAIL,  // email only
        SMS,    // SMS only
        ALL     // all three channels
    }
}
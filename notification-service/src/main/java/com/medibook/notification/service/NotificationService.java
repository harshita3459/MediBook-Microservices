package com.medibook.notification.service;

import com.medibook.notification.dto.NotificationResponse;
import com.medibook.notification.entity.Notification;

import java.util.List;

public interface NotificationService {

    // ── Sending ────────────────────────────────────────────────────────────────

    /** Save notification to DB and dispatch via the requested channel */
    NotificationResponse send(Long recipientId, Notification.NotificationType type,
                              String title, String message,
                              Notification.NotificationChannel channel,
                              Long relatedId, String relatedType);

    /** Send to multiple recipients at once — used for admin broadcast */
    void sendBulk(List<Long> recipientIds, String title, String message,
                  Notification.NotificationChannel channel);

    // ── Email and SMS ──────────────────────────────────────────────────────────

    void sendEmail(String toEmail, String subject, String body);

    void sendSms(String phoneNumber, String message);

    // ── Retrieval ──────────────────────────────────────────────────────────────

    List<NotificationResponse> getByRecipient(Long recipientId);

    List<NotificationResponse> getUnreadByRecipient(Long recipientId);

    long getUnreadCount(Long recipientId);

    // ── State management ───────────────────────────────────────────────────────

    NotificationResponse markAsRead(Long notificationId);

    int markAllAsRead(Long recipientId);

    void deleteNotification(Long notificationId);

    // ── Admin ──────────────────────────────────────────────────────────────────

    List<NotificationResponse> getAll();
}
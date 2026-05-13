package com.medibook.notification.service;

import com.medibook.notification.dto.NotificationResponse;
import com.medibook.notification.entity.Notification;
import com.medibook.notification.entity.Notification.NotificationChannel;
import com.medibook.notification.entity.Notification.NotificationType;
import com.medibook.notification.exception.NotificationNotFoundException;
import com.medibook.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender          mailSender;

    @Value("${notification.email.from}")
    private String fromEmail;

    @Value("${notification.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${notification.sms.enabled:false}")
    private boolean smsEnabled;

    // ── Send ───────────────────────────────────────────────────────────────────

    @Override
    public NotificationResponse send(Long recipientId, NotificationType type,
                                     String title, String message,
                                     NotificationChannel channel,
                                     Long relatedId, String relatedType) {

        // Always save to DB — this is the in-app notification store
        Notification notification = Notification.builder()
                .recipientId(recipientId)
                .type(type)
                .title(title)
                .message(message)
                .channel(channel)
                .relatedId(relatedId)
                .relatedType(relatedType)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification saved: type={} recipientId={}", type, recipientId);

        // Dispatch via requested channel(s) — failures are logged but never thrown
        // so a broken SMTP server never crashes the notification save
        if (channel == NotificationChannel.EMAIL || channel == NotificationChannel.ALL) {
            log.debug("Email dispatch requested for recipientId={} — implement email lookup from auth-service", recipientId);
            // In production: call auth-service to get the email address for recipientId,
            // then call sendEmail(). For now we log the intent.
        }

        if (channel == NotificationChannel.SMS || channel == NotificationChannel.ALL) {
            log.debug("SMS dispatch requested for recipientId={} — implement phone lookup from auth-service", recipientId);
        }

        return NotificationResponse.from(saved);
    }

    // ── Send Bulk ──────────────────────────────────────────────────────────────

    @Override
    public void sendBulk(List<Long> recipientIds, String title,
                         String message, NotificationChannel channel) {
        log.info("Bulk notification: {} recipients, channel={}", recipientIds.size(), channel);
        for (Long recipientId : recipientIds) {
            try {
                send(recipientId, NotificationType.BULK_MESSAGE,
                     title, message, channel, null, null);
            } catch (Exception ex) {
                // One failed recipient should not block the rest
                log.error("Failed to send bulk notification to recipientId={}: {}",
                        recipientId, ex.getMessage());
            }
        }
    }

    // ── Email ──────────────────────────────────────────────────────────────────

    @Override
    public void sendEmail(String toEmail, String subject, String body) {
        if (!emailEnabled) {
            log.info("Email disabled — skipping send to {} subject={}", toEmail, subject);
            return;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(fromEmail);
            mail.setTo(toEmail);
            mail.setSubject(subject);
            mail.setText(body);
            mailSender.send(mail);
            log.info("Email sent to {}", toEmail);
        } catch (Exception ex) {
            // Log and continue — email failure never blocks the main flow
            log.error("Email send failed to {}: {}", toEmail, ex.getMessage());
        }
    }

    // ── SMS (stub — plug in Twilio when ready) ─────────────────────────────────

    @Override
    public void sendSms(String phoneNumber, String message) {
        if (!smsEnabled) {
            log.info("SMS disabled — skipping send to {} message={}", phoneNumber, message);
            return;
        }
        // TODO: integrate Twilio SDK here
        // Message.creator(new PhoneNumber(phoneNumber),
        //     new PhoneNumber(twilioFromNumber), message).create();
        log.info("SMS (stub) to {}: {}", phoneNumber, message);
    }

    // ── Retrieval ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getByRecipient(Long recipientId) {
        return notificationRepository
                .findByRecipientIdOrderBySentAtDesc(recipientId)
                .stream().map(NotificationResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadByRecipient(Long recipientId) {
        return notificationRepository
                .findByRecipientIdAndIsReadFalseOrderBySentAtDesc(recipientId)
                .stream().map(NotificationResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long recipientId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
    }

    // ── State management ───────────────────────────────────────────────────────

    @Override
    public NotificationResponse markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        notification.setRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Override
    public int markAllAsRead(Long recipientId) {
        int updated = notificationRepository.markAllAsRead(recipientId);
        log.info("Marked {} notifications as read for recipientId={}", updated, recipientId);
        return updated;
    }

    @Override
    public void deleteNotification(Long notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new NotificationNotFoundException(notificationId);
        }
        notificationRepository.deleteById(notificationId);
        log.info("Notification deleted: id={}", notificationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getAll() {
        return notificationRepository.findAll()
                .stream().map(NotificationResponse::from).toList();
    }
}
package com.medibook.notification.dto;

import com.medibook.notification.entity.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

// ── NotificationResponse ──────────────────────────────────────────────────────

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationResponse {

    private Long     notificationId;
    private Long     recipientId;
    private String   type;
    private String   title;
    private String   message;
    private String   channel;
    private Long     relatedId;
    private String   relatedType;
    private boolean  isRead;
    private LocalDateTime sentAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .notificationId(n.getNotificationId())
                .recipientId(n.getRecipientId())
                .type(n.getType().name())
                .title(n.getTitle())
                .message(n.getMessage())
                .channel(n.getChannel().name())
                .relatedId(n.getRelatedId())
                .relatedType(n.getRelatedType())
                .isRead(n.isRead())
                .sentAt(n.getSentAt())
                .build();
    }
}
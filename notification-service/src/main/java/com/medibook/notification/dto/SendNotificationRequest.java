package com.medibook.notification.dto;

import com.medibook.notification.entity.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class SendNotificationRequest {

    @NotNull(message = "Recipient ID is required")
    private Long recipientId;

    @NotNull(message = "Notification type is required")
    private Notification.NotificationType type;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Message is required")
    private String message;

    private Notification.NotificationChannel channel = Notification.NotificationChannel.APP;
    private Long   relatedId;
    private String relatedType;

    public Long getRecipientId()    { return recipientId; }
    public Notification.NotificationType getType() { return type; }
    public String getTitle()        { return title; }
    public String getMessage()      { return message; }
    public Notification.NotificationChannel getChannel() { return channel; }
    public Long getRelatedId()      { return relatedId; }
    public String getRelatedType()  { return relatedType; }
}
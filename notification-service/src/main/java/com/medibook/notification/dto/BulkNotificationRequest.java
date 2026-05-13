package com.medibook.notification.dto;

import com.medibook.notification.entity.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

//Admin broadcasts to multiple users at once

public class BulkNotificationRequest {

    /** List of userIds to notify. Empty = send to ALL users (admin broadcast) */
	private List<Long> recipientIds;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Message is required")
    private String message;

    private Notification.NotificationChannel channel = Notification.NotificationChannel.APP;

    public List<Long> getRecipientIds() { return recipientIds; }
    public String getTitle()   { return title; }
    public String getMessage() { return message; }
    public Notification.NotificationChannel getChannel() { return channel; }
}
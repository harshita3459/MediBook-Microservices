package com.medibook.notification.repository;

import com.medibook.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** All notifications for a recipient, newest first — used in notification center */
    List<Notification> findByRecipientIdOrderBySentAtDesc(Long recipientId);

    /** Only unread notifications — used for notification bell with unread items */
    List<Notification> findByRecipientIdAndIsReadFalseOrderBySentAtDesc(Long recipientId);

    /** Count unread — used for the red badge number on the bell icon */
    long countByRecipientIdAndIsReadFalse(Long recipientId);

    /** Notifications by type — used by admin to audit specific event types */
    List<Notification> findByTypeOrderBySentAtDesc(Notification.NotificationType type);

    /** Mark all unread as read for a recipient — "mark all read" button */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientId = :recipientId AND n.isRead = false")
    int markAllAsRead(@Param("recipientId") Long recipientId);

    /** Find notifications linked to a specific entity (e.g. all notifications for appointmentId=5) */
    List<Notification> findByRelatedIdAndRelatedType(Long relatedId, String relatedType);
}
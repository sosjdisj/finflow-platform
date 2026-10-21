package org.example.finflowbackend.notification.dto;

import org.example.finflowbackend.notification.entity.Notification;

import java.time.LocalDateTime;

/**
 * 通知记录条目。
 */
public record NotificationResponse(Long id, String templateCode, String eventId, String receiverType,
                                       Long receiverId, String title, String content, LocalDateTime readAt,
                                       String inAppStatus, String emailStatus, String emailError,
                                       LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getTemplateCode(),
                notification.getEventId(), notification.getReceiverType(), notification.getReceiverId(),
                notification.getTitle(), notification.getContent(), notification.getReadAt(),
                notification.getInAppStatus(), notification.getEmailStatus(), notification.getEmailError(),
                notification.getCreatedAt());
    }
}

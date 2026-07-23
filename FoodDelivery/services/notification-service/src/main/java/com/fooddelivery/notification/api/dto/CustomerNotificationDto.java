package com.fooddelivery.notification.api.dto;

import com.fooddelivery.notification.domain.model.Notification;
import com.fooddelivery.notification.domain.model.valueobject.Channel;
import com.fooddelivery.notification.domain.model.valueobject.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerNotificationDto(
        UUID id,
        String type,
        Channel channel,
        String title,
        String body,
        String entityType,
        UUID entityId,
        boolean read,
        Instant readAt,
        NotificationStatus status,
        Instant sentAt,
        Instant createdAt
) {
    public static CustomerNotificationDto from(Notification notification) {
        return new CustomerNotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getChannel(),
                notification.getTitle(),
                notification.getBody(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getStatus(),
                notification.getSentAt(),
                notification.getCreatedAt());
    }
}

package com.fooddelivery.notification.application.dispatch;

import com.fooddelivery.notification.domain.model.valueobject.Channel;

import java.util.UUID;

public interface NotificationDispatchGateway {

    void send(DispatchMessage message);

    record DispatchMessage(
            UUID notificationId,
            UUID userId,
            Channel channel,
            String title,
            String body
    ) {}
}

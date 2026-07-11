package com.fooddelivery.notification.infrastructure.dispatch;

import com.fooddelivery.notification.application.dispatch.NotificationDispatchGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotificationDispatchGateway implements NotificationDispatchGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationDispatchGateway.class);

    @Override
    public void send(DispatchMessage message) {
        log.info("Notification dispatched: id={}, userId={}, channel={}, title={}",
                message.notificationId(), message.userId(), message.channel(), message.title());
    }
}

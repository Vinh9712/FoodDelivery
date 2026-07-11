package com.fooddelivery.notification.application.dispatch;

import com.fooddelivery.notification.domain.model.Notification;
import com.fooddelivery.notification.infrastructure.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationJobProcessor.class);

    private final NotificationRepository notificationRepository;
    private final NotificationDispatchGateway dispatchGateway;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;
    private final int maxAttempts;

    public NotificationJobProcessor(
            NotificationRepository notificationRepository,
            NotificationDispatchGateway dispatchGateway,
            @Value("${app.notification.dispatch.retry-base-delay:5s}") Duration retryBaseDelay,
            @Value("${app.notification.dispatch.retry-max-delay:10m}") Duration retryMaxDelay,
            @Value("${app.notification.dispatch.max-attempts:5}") int maxAttempts) {
        this.notificationRepository = notificationRepository;
        this.dispatchGateway = dispatchGateway;
        this.retryBaseDelay = retryBaseDelay;
        this.retryMaxDelay = retryMaxDelay;
        this.maxAttempts = maxAttempts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(UUID notificationId) {
        Notification notification = notificationRepository.findByIdForUpdate(notificationId).orElse(null);
        Instant now = Instant.now();
        if (notification == null || !notification.canDispatch(now)) {
            return;
        }

        try {
            notification.markSending();
            dispatchGateway.send(new NotificationDispatchGateway.DispatchMessage(
                    notification.getId(), notification.getUserId(), notification.getChannel(),
                    notification.getTitle(), notification.getBody()));
            notification.markSent();
            log.info("Notification job {} sent using {}", notification.getId(), notification.getChannel());
        } catch (Exception ex) {
            int attempt = notification.getRetryCount() + 1;
            notification.recordFailure(rootMessage(ex), now.plus(backoffFor(attempt)), maxAttempts);
            log.warn("Notification job {} failed on attempt {} with status {}",
                    notification.getId(), attempt, notification.getStatus());
        }
    }

    private Duration backoffFor(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        long delay;
        try {
            delay = Math.multiplyExact(retryBaseDelay.toMillis(), multiplier);
        } catch (ArithmeticException ex) {
            delay = retryMaxDelay.toMillis();
        }
        return Duration.ofMillis(Math.min(delay, retryMaxDelay.toMillis()));
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}

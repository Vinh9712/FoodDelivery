package com.fooddelivery.notification.application.dispatch;

import com.fooddelivery.notification.domain.model.valueobject.NotificationStatus;
import com.fooddelivery.notification.infrastructure.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.notification.dispatch.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationJobScheduler {

    private final NotificationRepository notificationRepository;
    private final NotificationJobProcessor processor;

    @Scheduled(fixedDelayString = "${app.notification.dispatch.poll-delay:2s}")
    public void dispatchDueJobs() {
        List<NotificationStatus> statuses = List.of(
                NotificationStatus.PENDING, NotificationStatus.RETRY_SCHEDULED);
        for (UUID notificationId : notificationRepository.findDueJobIds(
                statuses, Instant.now(), PageRequest.of(0, 50))) {
            processor.processOne(notificationId);
        }
    }
}

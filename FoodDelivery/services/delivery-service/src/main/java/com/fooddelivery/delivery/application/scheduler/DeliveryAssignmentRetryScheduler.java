package com.fooddelivery.delivery.application.scheduler;

import com.fooddelivery.delivery.application.service.DeliveryAssignmentService;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Retries FINDING_DRIVER deliveries without requiring order-service callbacks.
 * Safe under multi-replica deploy: each delivery is locked in REQUIRES_NEW.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.delivery.assignment.retry-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DeliveryAssignmentRetryScheduler {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAssignmentService assignmentService;

    @Scheduled(fixedDelayString = "${app.delivery.assignment.poll-delay:5s}")
    public void retryFindingDriverDeliveries() {
        for (UUID deliveryId : deliveryRepository.findDueAssignmentIds(
                Instant.now(), PageRequest.of(0, 50))) {
            assignmentService.retryAssignment(deliveryId);
        }
    }
}

package com.fooddelivery.delivery.application.outbox;

import com.fooddelivery.delivery.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Polls due delivery outbox events in small batches and delegates publish to
 * {@link DeliveryOutboxPublisher}. Safe under multi-replica deployment because
 * each event is locked with SKIP LOCKED inside the publisher.
 * <p>
 * Does not hold a single transaction across the whole batch.
 * </p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.delivery.outbox.relay.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DeliveryOutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final DeliveryOutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${app.delivery.outbox.relay.poll-delay:2s}")
    public void relayPendingEvents() {
        for (UUID eventId : outboxEventRepository.findDueEventIds(
                Instant.now(), PageRequest.of(0, 50))) {
            publisher.publishOne(eventId);
        }
    }
}

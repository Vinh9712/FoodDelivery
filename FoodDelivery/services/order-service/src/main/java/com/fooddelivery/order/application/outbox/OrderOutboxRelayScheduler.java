package com.fooddelivery.order.application.outbox;

import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Polls due order outbox events in small batches and delegates publish to
 * {@link OrderOutboxPublisher}. Safe under multi-replica deployment because
 * each event is locked with SKIP LOCKED inside the publisher.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.order.outbox.relay.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OrderOutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final OrderOutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${app.order.outbox.relay.poll-delay:2s}")
    public void relayPendingEvents() {
        for (UUID eventId : outboxEventRepository.findDueEventIds(
                Instant.now(), PageRequest.of(0, 50))) {
            publisher.publishOne(eventId);
        }
    }
}

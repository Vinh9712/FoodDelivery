package com.fooddelivery.order.application.outbox;

import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>
 * Due selection is restricted to the head unpublished event per aggregate so
 * per-aggregate ordering is preserved even when earlier events fail/retry.
 * </p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.order.outbox.relay.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OrderOutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxRelayScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OrderOutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${app.order.outbox.relay.poll-delay:2s}")
    public void relayPendingEvents() {
        for (UUID eventId : outboxEventRepository.findDueEventIds(
                Instant.now(), PageRequest.of(0, 50))) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Order outbox relay interrupted; stopping current poll");
                return;
            }
            publisher.publishOne(eventId);
        }
    }
}

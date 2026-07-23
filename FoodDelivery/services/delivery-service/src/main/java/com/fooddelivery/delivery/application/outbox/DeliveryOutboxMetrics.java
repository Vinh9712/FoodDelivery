package com.fooddelivery.delivery.application.outbox;

import com.fooddelivery.delivery.infrastructure.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Micrometer gauges/counters for delivery outbox backlog (plan reliability metric names).
 */
@Component
public class DeliveryOutboxMetrics {

    private final Counter publishRetryTotal;
    private final Counter deadLetterTotal;

    public DeliveryOutboxMetrics(
            MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository, Clock clock) {
        Gauge.builder("outbox_pending", outboxEventRepository, repo -> repo.countPending() + repo.countRetry())
                .description("Unpublished non-dead-letter outbox events")
                .tag("service", "delivery")
                .register(meterRegistry);
        Gauge.builder("outbox_oldest_unpublished_seconds", outboxEventRepository, repo -> {
                    Instant oldest = repo.findOldestUnpublishedOccurredAt();
                    if (oldest == null) {
                        return 0.0;
                    }
                    return Math.max(0, clock.instant().getEpochSecond() - oldest.getEpochSecond());
                })
                .description("Age in seconds of oldest unpublished outbox event")
                .tag("service", "delivery")
                .register(meterRegistry);
        Gauge.builder("delivery.outbox.pending", outboxEventRepository, OutboxEventRepository::countPending)
                .description("Delivery outbox events never attempted (unpublished, attempts=0)")
                .register(meterRegistry);
        Gauge.builder("delivery.outbox.retry", outboxEventRepository, OutboxEventRepository::countRetry)
                .description("Delivery outbox events awaiting retry (unpublished, attempts>0)")
                .register(meterRegistry);
        Gauge.builder("delivery.outbox.dead_letter", outboxEventRepository, OutboxEventRepository::countDeadLettered)
                .description("Delivery outbox events moved to dead letter")
                .register(meterRegistry);

        this.publishRetryTotal = Counter.builder("outbox_publish_retry_total")
                .description("Outbox publish failures scheduled for retry")
                .tag("service", "delivery")
                .register(meterRegistry);
        this.deadLetterTotal = Counter.builder("outbox_dead_letter_total")
                .description("Outbox events moved to dead letter")
                .tag("service", "delivery")
                .register(meterRegistry);
    }

    public void recordPublishRetry() {
        publishRetryTotal.increment();
    }

    public void recordDeadLetter() {
        deadLetterTotal.increment();
    }
}

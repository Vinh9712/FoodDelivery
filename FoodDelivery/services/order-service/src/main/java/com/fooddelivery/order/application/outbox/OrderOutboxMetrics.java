package com.fooddelivery.order.application.outbox;

import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Micrometer gauges/counters for order outbox backlog (plan reliability metric names).
 */
@Component
public class OrderOutboxMetrics {

    private final Counter publishRetryTotal;
    private final Counter deadLetterTotal;

    public OrderOutboxMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository, Clock clock) {
        Gauge.builder("outbox_pending", outboxEventRepository, repo -> repo.countPending() + repo.countRetry())
                .description("Unpublished non-dead-letter outbox events")
                .tag("service", "order")
                .register(meterRegistry);
        Gauge.builder("outbox_oldest_unpublished_seconds", outboxEventRepository, repo -> {
                    Instant oldest = repo.findOldestUnpublishedCreatedAt();
                    if (oldest == null) {
                        return 0.0;
                    }
                    return Math.max(0, clock.instant().getEpochSecond() - oldest.getEpochSecond());
                })
                .description("Age in seconds of oldest unpublished outbox event")
                .tag("service", "order")
                .register(meterRegistry);
        // Backward-compatible dotted gauges
        Gauge.builder("order.outbox.pending", outboxEventRepository, OutboxEventRepository::countPending)
                .description("Order outbox events never attempted (unpublished, attempts=0)")
                .register(meterRegistry);
        Gauge.builder("order.outbox.retry", outboxEventRepository, OutboxEventRepository::countRetry)
                .description("Order outbox events awaiting retry (unpublished, attempts>0)")
                .register(meterRegistry);
        Gauge.builder("order.outbox.dead_letter", outboxEventRepository, OutboxEventRepository::countDeadLettered)
                .description("Order outbox events moved to dead letter")
                .register(meterRegistry);

        this.publishRetryTotal = Counter.builder("outbox_publish_retry_total")
                .description("Outbox publish failures scheduled for retry")
                .tag("service", "order")
                .register(meterRegistry);
        this.deadLetterTotal = Counter.builder("outbox_dead_letter_total")
                .description("Outbox events moved to dead letter")
                .tag("service", "order")
                .register(meterRegistry);
    }

    public void recordPublishRetry() {
        publishRetryTotal.increment();
    }

    public void recordDeadLetter() {
        deadLetterTotal.increment();
    }
}

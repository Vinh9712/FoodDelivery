package com.fooddelivery.payment.application.outbox;

import com.fooddelivery.payment.infrastructure.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Micrometer gauges/counters for payment outbox backlog.
 */
@Component
public class PaymentOutboxMetrics {

    private final Counter publishRetryTotal;
    private final Counter deadLetterTotal;

    public PaymentOutboxMetrics(
            MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository, Clock clock) {
        Gauge.builder("outbox_pending", outboxEventRepository, repo -> repo.countPending() + repo.countRetry())
                .description("Unpublished non-dead-letter outbox events")
                .tag("service", "payment")
                .register(meterRegistry);
        Gauge.builder("outbox_oldest_unpublished_seconds", outboxEventRepository, repo -> {
                    Instant oldest = repo.findOldestUnpublishedOccurredAt();
                    if (oldest == null) {
                        return 0.0;
                    }
                    return Math.max(0, clock.instant().getEpochSecond() - oldest.getEpochSecond());
                })
                .description("Age in seconds of oldest unpublished outbox event")
                .tag("service", "payment")
                .register(meterRegistry);

        this.publishRetryTotal = Counter.builder("outbox_publish_retry_total")
                .description("Outbox publish failures scheduled for retry")
                .tag("service", "payment")
                .register(meterRegistry);
        this.deadLetterTotal = Counter.builder("outbox_dead_letter_total")
                .description("Outbox events moved to dead letter")
                .tag("service", "payment")
                .register(meterRegistry);
    }

    public void recordPublishRetry() {
        publishRetryTotal.increment();
    }

    public void recordDeadLetter() {
        deadLetterTotal.increment();
    }
}

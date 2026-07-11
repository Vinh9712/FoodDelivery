package com.fooddelivery.delivery.application.outbox;

import com.fooddelivery.delivery.infrastructure.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer gauges for delivery outbox backlog: pending / retry / dead-letter.
 */
@Component
public class DeliveryOutboxMetrics {

    public DeliveryOutboxMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository) {
        Gauge.builder("delivery.outbox.pending", outboxEventRepository, OutboxEventRepository::countPending)
                .description("Delivery outbox events never attempted (unpublished, attempts=0)")
                .register(meterRegistry);
        Gauge.builder("delivery.outbox.retry", outboxEventRepository, OutboxEventRepository::countRetry)
                .description("Delivery outbox events awaiting retry (unpublished, attempts>0)")
                .register(meterRegistry);
        Gauge.builder("delivery.outbox.dead_letter", outboxEventRepository, OutboxEventRepository::countDeadLettered)
                .description("Delivery outbox events moved to dead letter")
                .register(meterRegistry);
    }
}

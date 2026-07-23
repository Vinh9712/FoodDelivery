package com.fooddelivery.order.application.outbox;

import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer gauges for order outbox backlog: pending / retry / dead-letter.
 */
@Component
public class OrderOutboxMetrics {

    public OrderOutboxMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository) {
        Gauge.builder("order.outbox.pending", outboxEventRepository, OutboxEventRepository::countPending)
                .description("Order outbox events never attempted (unpublished, attempts=0)")
                .register(meterRegistry);
        Gauge.builder("order.outbox.retry", outboxEventRepository, OutboxEventRepository::countRetry)
                .description("Order outbox events awaiting retry (unpublished, attempts>0)")
                .register(meterRegistry);
        Gauge.builder("order.outbox.dead_letter", outboxEventRepository, OutboxEventRepository::countDeadLettered)
                .description("Order outbox events moved to dead letter")
                .register(meterRegistry);
    }
}

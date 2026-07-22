package com.fooddelivery.order.application.messaging;

import com.fooddelivery.order.infrastructure.persistence.DeferredIntegrationEvent.Status;
import com.fooddelivery.order.infrastructure.repository.DeferredIntegrationEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Inbox deferred / gap metrics for order-service consumers.
 */
@Component
public class OrderInboxMetrics {

    private final Counter deferredTotal;
    private final Counter gapTotal;

    public OrderInboxMetrics(MeterRegistry meterRegistry, DeferredIntegrationEventRepository deferredRepository) {
        Gauge.builder(
                        "integration_event_deferred",
                        deferredRepository,
                        repo -> repo.countByStatus(Status.WAITING_FOR_PREDECESSOR))
                .description("Deferred integration events waiting for predecessor")
                .tag("service", "order")
                .register(meterRegistry);

        this.deferredTotal = Counter.builder("integration_event_deferred_total")
                .description("Times an integration event was deferred due to sequence gap")
                .tag("service", "order")
                .register(meterRegistry);
        this.gapTotal = Counter.builder("integration_event_gap_total")
                .description("Sequence gaps dead-lettered after gap window")
                .tag("service", "order")
                .register(meterRegistry);
    }

    public void recordDeferred() {
        deferredTotal.increment();
    }

    public void recordGap() {
        gapTotal.increment();
    }
}

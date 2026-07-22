package com.fooddelivery.delivery.application.messaging;

import com.fooddelivery.delivery.infrastructure.persistence.DeferredIntegrationEvent.Status;
import com.fooddelivery.delivery.infrastructure.repository.DeferredIntegrationEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Inbox deferred gauge for delivery-service order consumer.
 */
@Component
public class DeliveryInboxMetrics {

    public DeliveryInboxMetrics(MeterRegistry meterRegistry, DeferredIntegrationEventRepository deferredRepository) {
        Gauge.builder(
                        "integration_event_deferred",
                        deferredRepository,
                        repo -> repo.countByStatus(Status.WAITING_FOR_PREDECESSOR))
                .description("Deferred integration events waiting for predecessor")
                .tag("service", "delivery")
                .register(meterRegistry);
    }
}

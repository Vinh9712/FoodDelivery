package com.fooddelivery.order.saga;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Delivery schedule reconciliation outcome counters.
 */
@Component
public class OrderDeliveryReconciliationMetrics {

    private final MeterRegistry meterRegistry;

    public OrderDeliveryReconciliationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // Pre-register common outcomes so scrapers see zero series
        for (String outcome : new String[]{"attached", "retried", "attention", "compensated", "caught_up"}) {
            Counter.builder("order_delivery_reconciliation_total")
                    .tag("outcome", outcome)
                    .register(meterRegistry);
        }
    }

    public void record(String outcome) {
        meterRegistry.counter("order_delivery_reconciliation_total", "outcome", outcome).increment();
    }
}

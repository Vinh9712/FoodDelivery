package com.fooddelivery.order.saga;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Refund reconciliation outcome counters (also incremented from OrderCompensationService).
 */
@Component
public class OrderRefundReconciliationMetrics {

    private final MeterRegistry meterRegistry;

    public OrderRefundReconciliationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        for (String outcome : new String[]{"confirmed", "backoff", "manual_review"}) {
            Counter.builder("order_refund_reconciliation_total")
                    .tag("outcome", outcome)
                    .register(meterRegistry);
        }
    }

    public void record(String outcome) {
        meterRegistry.counter("order_refund_reconciliation_total", "outcome", outcome).increment();
    }
}

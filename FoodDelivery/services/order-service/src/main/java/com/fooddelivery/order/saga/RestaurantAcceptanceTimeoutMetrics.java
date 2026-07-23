package com.fooddelivery.order.saga;

import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Overdue PAID orders awaiting restaurant acceptance (or timeout claim).
 */
@Component
public class RestaurantAcceptanceTimeoutMetrics {

    public RestaurantAcceptanceTimeoutMetrics(
            MeterRegistry meterRegistry,
            OrderRepository orderRepository,
            Clock clock) {
        Gauge.builder(
                        "restaurant_acceptance_overdue",
                        orderRepository,
                        repo -> repo.countOverdueRestaurantAcceptance(clock.instant()))
                .description("PAID orders past restaurant_response_deadline")
                .register(meterRegistry);
    }
}

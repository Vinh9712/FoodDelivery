package com.fooddelivery.order.saga;

import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Scans PAID orders past restaurant_response_deadline and claims timeout exactly once.
 */
@Component
@ConditionalOnProperty(
        prefix = "order.restaurant-acceptance-timeout-scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RestaurantAcceptanceTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(RestaurantAcceptanceTimeoutScheduler.class);

    private final OrderRepository orderRepository;
    private final RestaurantAcceptanceTimeoutService timeoutService;
    private final Clock clock;
    private final int batchSize;

    public RestaurantAcceptanceTimeoutScheduler(
            OrderRepository orderRepository,
            RestaurantAcceptanceTimeoutService timeoutService,
            Clock clock,
            @Value("${order.restaurant-acceptance-timeout-scheduler.batch-size:50}") int batchSize) {
        this.orderRepository = orderRepository;
        this.timeoutService = timeoutService;
        this.clock = clock;
        this.batchSize = batchSize > 0 ? batchSize : 50;
    }

    @Scheduled(fixedDelayString = "${order.restaurant-acceptance-timeout-scheduler.scan-interval:15s}")
    public void scanOverdueOrders() {
        List<UUID> overdue = orderRepository.findOverdueRestaurantAcceptanceOrderIds(
                clock.instant(), PageRequest.of(0, batchSize));
        for (UUID orderId : overdue) {
            try {
                timeoutService.processCandidate(orderId);
            } catch (RuntimeException ex) {
                log.warn("Restaurant acceptance timeout failed for order {}: {}", orderId, ex.getMessage());
            }
        }
    }
}

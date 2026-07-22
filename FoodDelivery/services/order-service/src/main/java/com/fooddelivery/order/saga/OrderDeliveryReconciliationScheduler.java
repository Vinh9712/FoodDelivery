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
 * Scans READY_FOR_PICKUP orders due for delivery schedule reconciliation.
 */
@Component
@ConditionalOnProperty(
        prefix = "order.delivery-reconciliation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OrderDeliveryReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderDeliveryReconciliationScheduler.class);

    private final OrderRepository orderRepository;
    private final OrderDeliveryReconciliationService reconciliationService;
    private final Clock clock;
    private final int batchSize;

    public OrderDeliveryReconciliationScheduler(
            OrderRepository orderRepository,
            OrderDeliveryReconciliationService reconciliationService,
            Clock clock,
            @Value("${order.delivery-reconciliation.batch-size:50}") int batchSize) {
        this.orderRepository = orderRepository;
        this.reconciliationService = reconciliationService;
        this.clock = clock;
        this.batchSize = batchSize > 0 ? batchSize : 50;
    }

    @Scheduled(fixedDelayString = "${order.delivery-reconciliation.scan-interval:30s}")
    public void reconcileDueOrders() {
        List<UUID> due = orderRepository.findDueDeliveryReconciliationOrderIds(
                clock.instant(), PageRequest.of(0, batchSize));
        for (UUID orderId : due) {
            try {
                reconciliationService.reconcile(orderId);
            } catch (RuntimeException ex) {
                log.warn("Delivery reconciliation failed for order {}: {}", orderId, ex.getMessage());
            }
        }
    }
}

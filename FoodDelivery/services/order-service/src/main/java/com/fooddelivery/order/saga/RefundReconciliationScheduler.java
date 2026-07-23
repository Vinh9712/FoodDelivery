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
 * Retries pending refunds for CANCELLATION_PENDING orders (lookup before POST).
 */
@Component
@ConditionalOnProperty(
        prefix = "order.refund-reconciliation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RefundReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefundReconciliationScheduler.class);

    private final OrderRepository orderRepository;
    private final OrderCompensationService compensationService;
    private final Clock clock;
    private final int batchSize;

    public RefundReconciliationScheduler(
            OrderRepository orderRepository,
            OrderCompensationService compensationService,
            Clock clock,
            @Value("${order.refund-reconciliation.batch-size:50}") int batchSize) {
        this.orderRepository = orderRepository;
        this.compensationService = compensationService;
        this.clock = clock;
        this.batchSize = batchSize > 0 ? batchSize : 50;
    }

    @Scheduled(fixedDelayString = "${order.refund-reconciliation.scan-interval:30s}")
    public void reconcileDueRefunds() {
        List<UUID> due = orderRepository.findDueRefundReconciliationOrderIds(
                clock.instant(), PageRequest.of(0, batchSize));
        for (UUID orderId : due) {
            try {
                compensationService.reconcileRefund(orderId);
            } catch (RuntimeException ex) {
                log.warn("Refund reconciliation failed for order {}: {}", orderId, ex.getMessage());
            }
        }
    }
}

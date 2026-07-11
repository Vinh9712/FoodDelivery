package com.fooddelivery.order.saga;

import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.order.payment-reconciliation", name = "enabled", havingValue = "true")
public class OrderPaymentReconciliationScheduler {

    private final OrderRepository orderRepository;
    private final OrderSagaOrchestrator sagaOrchestrator;

    @Scheduled(fixedDelayString = "${app.order.payment-reconciliation.fixed-delay-ms:30000}")
    public void reconcilePendingOrders() {
        for (var order : orderRepository.findTop100ByStatusOrderByCreatedAtAsc(OrderStatus.PENDING)) {
            try {
                sagaOrchestrator.reconcilePayment(order.getId());
            } catch (RuntimeException ex) {
                log.warn("Payment reconciliation failed for order {}: {}", order.getId(), ex.getMessage());
            }
        }
    }
}

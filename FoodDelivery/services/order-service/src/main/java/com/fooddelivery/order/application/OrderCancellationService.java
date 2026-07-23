package com.fooddelivery.order.application;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.order.saga.OrderCompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Customer / admin initiated order cancellation.
 */
@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderCompensationService compensationService;
    private final Clock clock;

    @Transactional
    public Order cancel(UUID orderId, String reason, boolean admin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.CANCELLATION_PENDING) {
            return order;
        }

        CancellationCode code = admin ? CancellationCode.ADMIN_CANCELLED : CancellationCode.CUSTOMER_REQUESTED;
        OrderEventPayloads.Source source = admin
                ? OrderEventPayloads.Source.ADMIN
                : OrderEventPayloads.Source.CUSTOMER;

        if (order.getStatus() == OrderStatus.PENDING) {
            order.cancelUnpaid(reason, code, source, clock.instant());
            persist(order);
            return orderRepository.findDetailedById(orderId)
                    .orElse(order);
        }

        // Paid / in-progress: durable compensation + refund when applicable
        compensationService.start(orderId, code, reason, source);
        return orderRepository.findDetailedById(orderId)
                .orElseGet(() -> orderRepository.findById(orderId)
                        .orElseThrow(() -> new OrderNotFoundException(orderId)));
    }

    private void persist(Order order) {
        if (!order.getPendingOutboxEvents().isEmpty()) {
            outboxEventRepository.saveAll(order.getPendingOutboxEvents());
            order.clearPendingOutboxEvents();
        }
        orderRepository.save(order);
    }
}

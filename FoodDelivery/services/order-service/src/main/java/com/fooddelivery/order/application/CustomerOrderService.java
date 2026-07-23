package com.fooddelivery.order.application;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.saga.OrderCompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private final OrderRepository orderRepository;
    private final OrderCompensationService compensationService;

    @Transactional(readOnly = true)
    public Page<Order> list(UUID customerId, OrderStatus status, Pageable pageable) {
        if (status != null) {
            return orderRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        }
        return orderRepository.findByCustomerId(customerId, pageable);
    }

    public Order cancel(UUID orderId, UUID customerId, String reason) {
        loadOwnedOrder(orderId, customerId);
        compensationService.start(
                orderId,
                CancellationCode.CUSTOMER_REQUESTED,
                reason,
                OrderEventPayloads.Source.CUSTOMER);
        return loadOwnedOrder(orderId, customerId);
    }

    private Order loadOwnedOrder(UUID orderId, UUID customerId) {
        return orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}

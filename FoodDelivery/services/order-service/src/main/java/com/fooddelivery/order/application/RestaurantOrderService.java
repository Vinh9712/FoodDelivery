package com.fooddelivery.order.application;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.DeliveryAddressSnapshot;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PickupAddressSnapshot;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantOrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher events;

    public record OrderReadyForPickup(
            UUID orderId,
            UUID customerId,
            UUID restaurantId,
            PickupAddressSnapshot pickup,
            DeliveryAddressSnapshot dropoff) {
    }

    @Transactional
    public Order accept(UUID orderId, UUID actorId) {
        Order order = load(orderId);
        order.acceptByRestaurant(actorId);
        persist(order);
        return order;
    }

    @Transactional
    public Order startPreparing(UUID orderId, UUID actorId) {
        Order order = load(orderId);
        order.startPreparing(actorId);
        persist(order);
        return order;
    }

    @Transactional
    public Order markReady(UUID orderId, UUID actorId) {
        Order order = load(orderId);
        boolean wasPreparing = order.getStatus() == OrderStatus.PREPARING;
        order.markReadyForPickup(actorId);
        persist(order);
        if (wasPreparing && order.getStatus() == OrderStatus.READY_FOR_PICKUP) {
            events.publishEvent(new OrderReadyForPickup(
                    order.getId(),
                    order.getCustomerId(),
                    order.getRestaurantId(),
                    order.getPickupAddressSnapshot(),
                    order.getDeliveryAddressSnapshot()));
        }
        return order;
    }

    @Transactional
    public Order reject(UUID orderId, UUID actorId, String reason) {
        Order order = load(orderId);
        order.requestCancellation(
                reason,
                CancellationCode.RESTAURANT_REJECTED,
                OrderEventPayloads.Source.RESTAURANT);
        persist(order);
        return order;
    }

    @Transactional(readOnly = true)
    public Page<Order> list(UUID restaurantId, OrderStatus status, Pageable pageable) {
        if (status != null) {
            return orderRepository.findByRestaurantIdAndStatus(restaurantId, status, pageable);
        }
        return orderRepository.findByRestaurantId(restaurantId, pageable);
    }

    private Order load(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private void persist(Order order) {
        if (!order.getPendingOutboxEvents().isEmpty()) {
            outboxEventRepository.saveAll(order.getPendingOutboxEvents());
            order.clearPendingOutboxEvents();
        }
        orderRepository.save(order);
    }
}

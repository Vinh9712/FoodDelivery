package com.fooddelivery.order.application.listener;

import com.fooddelivery.order.application.dto.DriverAssignedEvent;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.AssignedDriverInfo;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consumes {@code driver.assigned} events from Delivery Service.
 * Follows the Idempotent Consumer pattern via {@code processed_events} table.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DriverAssignedEventListener {

    private static final String CONSUMER_NAME = "order-service-driver-assigned";

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "driver.assigned", groupId = "order-service")
    @Transactional
    public void onDriverAssigned(DriverAssignedEvent event) {
        if (processedEventRepository.existsByEventIdAndConsumer(event.eventId(), CONSUMER_NAME)) {
            log.debug("Event {} already processed by {}, skipping", event.eventId(), CONSUMER_NAME);
            return;
        }

        UUID orderId = event.payload().orderId();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.assignDriver(AssignedDriverInfo.from(event.payload()));
        orderRepository.save(order);

        processedEventRepository.markProcessed(event.eventId(), CONSUMER_NAME);
        log.info("Driver {} assigned to order {}", event.payload().driver().driverId(), orderId);
    }
}

package com.fooddelivery.delivery.application.listener;

import com.fooddelivery.delivery.application.service.DeliveryAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@code order.placed} events from Order Service.
 * Creates a Delivery and auto-assigns the nearest available driver.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedEventListener {

    private final DeliveryAssignmentService deliveryAssignmentService;

    @KafkaListener(topics = "order.placed", groupId = "delivery-service")
    public void onOrderPlaced(OrderPlacedEvent event) {
        try {
            UUID orderId = event.payload().orderId();
            log.info("Received order.placed event for order {}", orderId);
            deliveryAssignmentService.autoAssignDriver(orderId);
        } catch (Exception e) {
            log.error("Failed to process order.placed event: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Typed DTO for the order.placed Kafka event envelope.
     */
    public record OrderPlacedEvent(
            UUID eventId,
            String eventType,
            OrderPlacedPayload payload
    ) {}

    public record OrderPlacedPayload(
            UUID orderId
    ) {}
}

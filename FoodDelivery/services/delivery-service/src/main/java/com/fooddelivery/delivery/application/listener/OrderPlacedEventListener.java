package com.fooddelivery.delivery.application.listener;

import com.fooddelivery.delivery.application.service.DeliveryAssignmentService;
import com.fooddelivery.delivery.infrastructure.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consumes {@code order.placed} events from Order Service.
 * Creates a Delivery and auto-assigns the nearest available driver.
 * Follows the Idempotent Consumer pattern via {@code processed_events} table.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedEventListener {

    private static final String CONSUMER_NAME = "delivery-service-order-placed";

    private final DeliveryAssignmentService deliveryAssignmentService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "order.placed", groupId = "delivery-service")
    @Transactional
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (event == null || event.eventId() == null || event.payload() == null
                || event.payload().orderId() == null || event.payload().customerId() == null
                || event.payload().deliveryAddress() == null || event.payload().deliveryAddress().isBlank()) {
            throw new IllegalArgumentException(
                    "Valid eventId, orderId, customerId, and deliveryAddress are required");
        }
        if (processedEventRepository.existsByEventIdAndConsumer(event.eventId(), CONSUMER_NAME)) {
            log.debug("Event {} already processed by {}, skipping", event.eventId(), CONSUMER_NAME);
            return;
        }

        try {
            OrderPlacedPayload payload = event.payload();
            log.info("Received order.placed event for order {}", payload.orderId());
            deliveryAssignmentService.autoAssignDriver(
                    payload.orderId(), payload.customerId(), payload.deliveryAddress());

            processedEventRepository.markProcessed(event.eventId(), CONSUMER_NAME);
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
            UUID orderId,
            UUID customerId,
            String deliveryAddress
    ) {}
}

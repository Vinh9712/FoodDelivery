package com.fooddelivery.delivery.application.listener;

import com.fooddelivery.delivery.application.service.DeliveryAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
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
    public void onOrderPlaced(Map<String, Object> event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            if (payload == null) {
                log.warn("Received order.placed event with null payload");
                return;
            }

            UUID orderId = UUID.fromString(payload.get("orderId").toString());
            log.info("Received order.placed event for order {}", orderId);
            deliveryAssignmentService.autoAssignDriver(orderId);
        } catch (Exception e) {
            log.error("Failed to process order.placed event: {}", e.getMessage(), e);
            throw e;
        }
    }
}

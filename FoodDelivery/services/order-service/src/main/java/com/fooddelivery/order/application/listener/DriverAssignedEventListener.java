package com.fooddelivery.order.application.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.order.application.dto.DriverAssignedPayload;
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

import java.util.Map;
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
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "driver.assigned", groupId = "order-service")
    @Transactional
    public void onDriverAssigned(Map<String, Object> event) {
        try {
            String eventIdStr = (String) event.get("eventId");
            UUID eventId = eventIdStr != null ? UUID.fromString(eventIdStr) : UUID.randomUUID();

            if (processedEventRepository.existsByEventIdAndConsumer(eventId, CONSUMER_NAME)) {
                log.debug("Event {} already processed by {}, skipping", eventId, CONSUMER_NAME);
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = (Map<String, Object>) event.get("payload");
            if (payloadMap == null) {
                log.warn("Received driver.assigned event with null payload");
                return;
            }

            // Convert the sub-map to DriverAssignedPayload DTO
            DriverAssignedPayload payload = objectMapper.convertValue(payloadMap, DriverAssignedPayload.class);

            UUID orderId = payload.orderId();
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));

            order.assignDriver(AssignedDriverInfo.from(payload));
            orderRepository.save(order);

            processedEventRepository.markProcessed(eventId, CONSUMER_NAME);
            log.info("Driver {} assigned to order {}", payload.driver().driverId(), orderId);

        } catch (Exception e) {
            log.error("Failed to process driver.assigned event: {}", e.getMessage(), e);
            throw e;
        }
    }
}

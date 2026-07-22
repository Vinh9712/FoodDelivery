package com.fooddelivery.order.application.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.delivery.DeliveryEventPayloads;
import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.AssignedDriverInfo;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.VehicleType;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.order.infrastructure.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single order-service consumer for the delivery family topic.
 * Applies driver assignment and lifecycle transitions with eventId dedupe.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryLifecycleEventListener {

    static final String CONSUMER_NAME = "order-service-delivery-lifecycle";

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.order.kafka.delivery-events-topic:delivery.events.v1}",
            groupId = "order-service")
    @Transactional
    public void onEvent(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("event payload is required");
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed delivery lifecycle event JSON", ex);
        }

        UUID eventId = requireEventId(root);
        if (processedEventRepository.existsByEventIdAndConsumer(eventId, CONSUMER_NAME)) {
            log.debug("Event {} already processed by {}, skipping", eventId, CONSUMER_NAME);
            return;
        }

        String eventType = textOrNull(root, "eventType");
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        JsonNode payloadNode = root.get("payload");
        if (payloadNode == null || payloadNode.isNull()) {
            throw new IllegalArgumentException("payload is required");
        }

        try {
            switch (eventType) {
                case EventContracts.DRIVER_ASSIGNED -> {
                    DeliveryEventPayloads.DriverAssigned payload = objectMapper.treeToValue(
                            payloadNode, DeliveryEventPayloads.DriverAssigned.class);
                    Order order = loadOrder(payload.orderId());
                    order.assignDriver(toAssignedDriver(payload));
                    persist(order, eventId);
                }
                case EventContracts.DELIVERY_PICKED_UP -> {
                    DeliveryEventPayloads.DeliveryPickedUp payload = objectMapper.treeToValue(
                            payloadNode, DeliveryEventPayloads.DeliveryPickedUp.class);
                    Order order = loadOrder(payload.orderId());
                    order.markPickedUp(payload.pickedUpAt(), OrderEventPayloads.Source.DELIVERY_EVENT);
                    persist(order, eventId);
                }
                case EventContracts.DELIVERY_IN_TRANSIT -> {
                    DeliveryEventPayloads.DeliveryInTransit payload = objectMapper.treeToValue(
                            payloadNode, DeliveryEventPayloads.DeliveryInTransit.class);
                    Order order = loadOrder(payload.orderId());
                    order.markDelivering(payload.deliveryStartedAt(), OrderEventPayloads.Source.DELIVERY_EVENT);
                    persist(order, eventId);
                }
                case EventContracts.DELIVERY_COMPLETED -> {
                    DeliveryEventPayloads.DeliveryCompleted payload = objectMapper.treeToValue(
                            payloadNode, DeliveryEventPayloads.DeliveryCompleted.class);
                    Order order = loadOrder(payload.orderId());
                    order.markDelivered(payload.deliveredAt(), OrderEventPayloads.Source.DELIVERY_EVENT);
                    persist(order, eventId);
                }
                case EventContracts.DELIVERY_FAILED -> {
                    DeliveryEventPayloads.DeliveryFailed payload = objectMapper.treeToValue(
                            payloadNode, DeliveryEventPayloads.DeliveryFailed.class);
                    Order order = loadOrder(payload.orderId());
                    order.requestCancellation(
                            payload.reason(),
                            CancellationCode.DELIVERY_FAILED,
                            OrderEventPayloads.Source.DELIVERY_EVENT);
                    persist(order, eventId);
                }
                default -> throw new IllegalArgumentException("Unsupported delivery event type: " + eventType);
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalArgumentException("Failed to process delivery lifecycle event", ex);
        }
    }

    private void persist(Order order, UUID eventId) {
        if (!order.getPendingOutboxEvents().isEmpty()) {
            outboxEventRepository.saveAll(order.getPendingOutboxEvents());
            order.clearPendingOutboxEvents();
        }
        orderRepository.save(order);
        processedEventRepository.markProcessed(eventId, CONSUMER_NAME);
        log.info("Applied delivery lifecycle event {} to order {}", eventId, order.getId());
    }

    private Order loadOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private static AssignedDriverInfo toAssignedDriver(DeliveryEventPayloads.DriverAssigned payload) {
        DeliveryEventPayloads.DriverSnapshot driver = payload.driver();
        return new AssignedDriverInfo(
                driver.driverId(),
                driver.fullName(),
                driver.phone(),
                VehicleType.valueOf(driver.vehicleType()),
                driver.licensePlate(),
                null,
                payload.assignedAt());
    }

    private static UUID requireEventId(JsonNode root) {
        String eventIdStr = textOrNull(root, "eventId");
        if (eventIdStr == null || eventIdStr.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        try {
            return UUID.fromString(eventIdStr);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("eventId is invalid", ex);
        }
    }

    private static String textOrNull(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }
}

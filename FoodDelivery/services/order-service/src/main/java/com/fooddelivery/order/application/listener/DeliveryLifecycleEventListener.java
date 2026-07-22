package com.fooddelivery.order.application.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.commonevents.delivery.DeliveryEventPayloads;
import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.application.messaging.ProcessDecision;
import com.fooddelivery.order.application.messaging.SequencedConsumer;
import com.fooddelivery.order.application.messaging.SequencedEventProcessor;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.AssignedDriverInfo;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.VehicleType;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.order.saga.OrderCompensationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single order-service consumer for the delivery family topic.
 * Routes every envelope through the sequenced inbox (dedupe / stale / defer / drain).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryLifecycleEventListener implements SequencedConsumer {

    /** Sequenced inbox consumer name (event-level dedupe + aggregate cursor). */
    public static final String CONSUMER_NAME = "order-delivery-v1";

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SequencedEventProcessor sequencedEventProcessor;
    private final OrderCompensationService compensationService;
    private final ObjectMapper objectMapper;

    @Override
    public String consumerName() {
        return CONSUMER_NAME;
    }

    @KafkaListener(
            topics = "${app.order.kafka.delivery-events-topic:delivery.events.v1}",
            groupId = "order-service")
    @Transactional
    public void onEvent(String rawJson) {
        IntegrationEventEnvelope<JsonNode> envelope = sequencedEventProcessor.parseAndValidate(rawJson);
        ProcessDecision decision = sequencedEventProcessor.process(
                CONSUMER_NAME, envelope, rawJson, this::handle);
        log.debug("Delivery lifecycle event {} decision={}", envelope.eventId(), decision);
    }

    @Override
    public void handle(IntegrationEventEnvelope<JsonNode> envelope) throws Exception {
        String eventType = envelope.eventType();
        JsonNode payloadNode = envelope.payload();
        switch (eventType) {
            case EventContracts.DRIVER_ASSIGNED -> {
                DeliveryEventPayloads.DriverAssigned payload = objectMapper.treeToValue(
                        payloadNode, DeliveryEventPayloads.DriverAssigned.class);
                Order order = loadOrder(payload.orderId());
                order.assignDriver(toAssignedDriver(payload));
                persist(order, envelope.eventId());
            }
            case EventContracts.DELIVERY_PICKED_UP -> {
                DeliveryEventPayloads.DeliveryPickedUp payload = objectMapper.treeToValue(
                        payloadNode, DeliveryEventPayloads.DeliveryPickedUp.class);
                Order order = loadOrder(payload.orderId());
                order.markPickedUp(payload.pickedUpAt(), OrderEventPayloads.Source.DELIVERY_EVENT);
                persist(order, envelope.eventId());
            }
            case EventContracts.DELIVERY_IN_TRANSIT -> {
                DeliveryEventPayloads.DeliveryInTransit payload = objectMapper.treeToValue(
                        payloadNode, DeliveryEventPayloads.DeliveryInTransit.class);
                Order order = loadOrder(payload.orderId());
                order.markDelivering(payload.deliveryStartedAt(), OrderEventPayloads.Source.DELIVERY_EVENT);
                persist(order, envelope.eventId());
            }
            case EventContracts.DELIVERY_COMPLETED -> {
                DeliveryEventPayloads.DeliveryCompleted payload = objectMapper.treeToValue(
                        payloadNode, DeliveryEventPayloads.DeliveryCompleted.class);
                Order order = loadOrder(payload.orderId());
                order.markDelivered(payload.deliveredAt(), OrderEventPayloads.Source.DELIVERY_EVENT);
                persist(order, envelope.eventId());
            }
            case EventContracts.DELIVERY_FAILED -> {
                DeliveryEventPayloads.DeliveryFailed payload = objectMapper.treeToValue(
                        payloadNode, DeliveryEventPayloads.DeliveryFailed.class);
                // Durable compensation: CANCELLATION_PENDING until refund confirmed
                compensationService.start(
                        payload.orderId(),
                        CancellationCode.DELIVERY_FAILED,
                        payload.reason(),
                        OrderEventPayloads.Source.DELIVERY_EVENT);
                log.info("Applied delivery failed event {} via compensation for order {}",
                        envelope.eventId(), payload.orderId());
            }
            default -> throw new IllegalArgumentException("Unsupported delivery event type: " + eventType);
        }
    }

    private void persist(Order order, UUID eventId) {
        if (!order.getPendingOutboxEvents().isEmpty()) {
            outboxEventRepository.saveAll(order.getPendingOutboxEvents());
            order.clearPendingOutboxEvents();
        }
        orderRepository.save(order);
        // processed_events row is written by SequencedEventProcessor after successful apply
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
}

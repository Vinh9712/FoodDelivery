package com.fooddelivery.delivery.application.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.delivery.application.messaging.ProcessDecision;
import com.fooddelivery.delivery.application.messaging.SequencedOrderEventProcessor;
import com.fooddelivery.delivery.application.service.DeliveryLifecycleService;
import com.fooddelivery.delivery.domain.model.Delivery;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consumes {@code order.events.v1} through sequenced inbox {@code delivery-order-v1}.
 * Only {@code OrderCancelled} mutates delivery; other order types advance sequence as no-ops.
 */
@Component
@Slf4j
public class OrderLifecycleEventListener {

    public static final String CONSUMER_NAME = "delivery-order-v1";
    public static final String AFTER_PICKUP_METRIC = "delivery_cancellation_after_pickup_total";

    private final SequencedOrderEventProcessor sequencedEventProcessor;
    private final DeliveryLifecycleService lifecycleService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public OrderLifecycleEventListener(
            SequencedOrderEventProcessor sequencedEventProcessor,
            DeliveryLifecycleService lifecycleService,
            ObjectMapper objectMapper,
            ObjectProvider<MeterRegistry> meterRegistry) {
        this.sequencedEventProcessor = sequencedEventProcessor;
        this.lifecycleService = lifecycleService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry.getIfAvailable(() -> Metrics.globalRegistry);
    }

    @KafkaListener(
            topics = "${app.delivery.kafka.order-events-topic:order.events.v1}",
            groupId = "delivery-service-order")
    @Transactional
    public void onEvent(String rawJson) {
        IntegrationEventEnvelope<JsonNode> envelope = sequencedEventProcessor.parseAndValidate(rawJson);
        ProcessDecision decision = sequencedEventProcessor.process(
                CONSUMER_NAME, envelope, rawJson, this::handle);
        log.debug("Order lifecycle event {} decision={}", envelope.eventId(), decision);
    }

    void handle(IntegrationEventEnvelope<JsonNode> envelope) throws Exception {
        if (!"Order".equals(envelope.aggregateType())) {
            throw new IllegalArgumentException("Unsupported order aggregateType: " + envelope.aggregateType());
        }
        String eventType = envelope.eventType();
        switch (eventType) {
            case EventContracts.ORDER_CREATED,
                 EventContracts.ORDER_STATUS_CHANGED,
                 EventContracts.ORDER_REFUND_STATUS_CHANGED -> {
                // Sequence-only no-ops so Order aggregate cursor advances without false gaps
                log.debug("Ignoring {} for delivery sequence advance", eventType);
            }
            case EventContracts.ORDER_CANCELLED -> {
                OrderEventPayloads.OrderCancelled payload = objectMapper.treeToValue(
                        envelope.payload(), OrderEventPayloads.OrderCancelled.class);
                applyCancelled(payload, envelope.aggregateId());
            }
            default -> throw new IllegalArgumentException("Unsupported order event type: " + eventType);
        }
    }

    private void applyCancelled(OrderEventPayloads.OrderCancelled payload, UUID aggregateId) {
        if (!payload.orderId().equals(aggregateId)) {
            throw new IllegalArgumentException("Order aggregateId does not match payload.orderId");
        }
        String reason = payload.reason() != null ? payload.reason() : "Order cancelled";
        Delivery.CancelFromOrderResult result = lifecycleService.cancelFromOrder(payload.orderId(), reason);
        if (result.afterPickup()) {
            meterRegistry.counter(AFTER_PICKUP_METRIC).increment();
        }
    }
}

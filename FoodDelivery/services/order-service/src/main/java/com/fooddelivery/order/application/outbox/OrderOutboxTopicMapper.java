package com.fooddelivery.order.application.outbox;

import com.fooddelivery.commonevents.EventContracts;
import org.springframework.stereotype.Component;

/**
 * Maps order domain outbox event types to the order family Kafka topic.
 * Delivery-family events (including DriverAssigned) are never published by order-service.
 */
@Component
public class OrderOutboxTopicMapper {

    public String topicFor(String eventType) {
        return switch (eventType) {
            case EventContracts.ORDER_CREATED,
                 EventContracts.ORDER_STATUS_CHANGED,
                 EventContracts.ORDER_CANCELLED -> EventContracts.ORDER_EVENTS_V1;
            default -> throw new IllegalArgumentException("Unsupported order event type: " + eventType);
        };
    }
}

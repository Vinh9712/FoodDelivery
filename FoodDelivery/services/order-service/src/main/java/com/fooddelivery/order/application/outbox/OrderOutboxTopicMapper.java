package com.fooddelivery.order.application.outbox;

import org.springframework.stereotype.Component;

/**
 * Maps order domain outbox event types to Kafka topics.
 */
@Component
public class OrderOutboxTopicMapper {

    public String topicFor(String eventType) {
        return switch (eventType) {
            case "OrderCreated" -> "order.placed";
            case "OrderCancelled" -> "order.cancelled";
            case "DriverAssigned" -> "order.driver-assigned";
            case "OrderStatusChanged" -> "order.status-changed";
            default -> throw new IllegalArgumentException("Unsupported order event type: " + eventType);
        };
    }
}

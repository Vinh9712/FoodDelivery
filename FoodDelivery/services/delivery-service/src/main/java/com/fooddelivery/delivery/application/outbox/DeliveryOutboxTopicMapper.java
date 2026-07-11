package com.fooddelivery.delivery.application.outbox;

import org.springframework.stereotype.Component;

/**
 * Maps delivery domain outbox event types to Kafka topics.
 */
@Component
public class DeliveryOutboxTopicMapper {

    public String topicFor(String eventType) {
        return switch (eventType) {
            case "driver.assigned", "DriverAssigned" -> "driver.assigned";
            case "delivery.picked-up", "DeliveryPickedUp" -> "delivery.picked-up";
            case "delivery.in-transit", "DeliveryInTransit" -> "delivery.in-transit";
            case "delivery.completed", "DeliveryCompleted" -> "delivery.completed";
            case "delivery.failed", "DeliveryFailed" -> "delivery.failed";
            default -> throw new IllegalArgumentException("Unsupported delivery event type: " + eventType);
        };
    }
}

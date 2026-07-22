package com.fooddelivery.delivery.application.outbox;

import com.fooddelivery.commonevents.EventContracts;
import org.springframework.stereotype.Component;

/**
 * Maps delivery domain outbox event types to the delivery family Kafka topic.
 */
@Component
public class DeliveryOutboxTopicMapper {

    public String topicFor(String eventType) {
        return switch (eventType) {
            case EventContracts.DRIVER_ASSIGNED,
                 EventContracts.DELIVERY_PICKED_UP,
                 EventContracts.DELIVERY_IN_TRANSIT,
                 EventContracts.DELIVERY_COMPLETED,
                 EventContracts.DELIVERY_FAILED,
                 // legacy aliases during cutover
                 "driver.assigned",
                 "delivery.picked-up",
                 "delivery.in-transit",
                 "delivery.completed",
                 "delivery.failed" -> EventContracts.DELIVERY_EVENTS_V1;
            default -> throw new IllegalArgumentException("Unsupported delivery event type: " + eventType);
        };
    }
}

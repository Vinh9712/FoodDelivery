package com.fooddelivery.payment.application.outbox;

import com.fooddelivery.commonevents.EventContracts;
import org.springframework.stereotype.Component;

/**
 * Maps payment domain outbox event types to the payment family Kafka topic.
 */
@Component
public class PaymentOutboxTopicMapper {

    public String topicFor(String eventType) {
        return switch (eventType) {
            case EventContracts.PAYMENT_SUCCEEDED,
                 EventContracts.PAYMENT_FAILED,
                 EventContracts.PAYMENT_REFUNDED -> EventContracts.PAYMENT_EVENTS_V1;
            default -> throw new IllegalArgumentException("Unsupported payment event type: " + eventType);
        };
    }
}

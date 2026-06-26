package com.fooddelivery.order.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes {@code order.placed} events to Kafka.
 *
 * <p>Event envelope:
 * <pre>
 * {
 *   "eventId":   "...",
 *   "eventType": "ORDER_PLACED",
 *   "timestamp": "2026-...",
 *   "payload": {
 *     "orderId":      "...",
 *     "customerId":   "...",
 *     "restaurantId": "...",
 *     "totalAmount":  150000,
 *     "currency":     "VND"
 *   }
 * }
 * </pre>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedEventPublisher {

    private static final String TOPIC = "order.placed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish order.placed event.
     *
     * @param orderId      the order id (used as Kafka message key for ordering)
     * @param customerId   who placed the order
     * @param restaurantId the restaurant
     * @param totalAmount  order total
     * @param currency     currency code (e.g. VND)
     */
    public void publish(UUID orderId, UUID customerId, UUID restaurantId,
                        BigDecimal totalAmount, String currency) {
        Map<String, Object> event = Map.of(
                "eventId",   UUID.randomUUID().toString(),
                "eventType", "ORDER_PLACED",
                "timestamp", Instant.now().toString(),
                "payload", Map.of(
                        "orderId",      orderId.toString(),
                        "customerId",   customerId.toString(),
                        "restaurantId", restaurantId.toString(),
                        "totalAmount",  totalAmount,
                        "currency",     currency
                )
        );

        kafkaTemplate.send(TOPIC, orderId.toString(), event);
        log.info("Published order.placed for order {}", orderId);
    }
}

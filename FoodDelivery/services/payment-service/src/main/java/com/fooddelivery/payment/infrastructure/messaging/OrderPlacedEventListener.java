package com.fooddelivery.payment.infrastructure.messaging;

import com.fooddelivery.payment.application.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for {@code order.placed} events from Order Service and
 * initiates a payment for each new order.
 *
 * <p>Event envelope expected:
 * <pre>
 * {
 *   "eventId": "...",
 *   "eventType": "ORDER_PLACED",
 *   "payload": {
 *     "orderId": "...",
 *     "totalAmount": 150000,
 *     "currency": "VND"
 *   }
 * }
 * </pre>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedEventListener {

    private final PaymentService paymentService;

    @KafkaListener(topics = "order.placed", groupId = "payment-service")
    public void onOrderPlaced(Map<String, Object> event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");

            UUID orderId = UUID.fromString((String) payload.get("orderId"));
            BigDecimal amount = new BigDecimal(payload.getOrDefault("totalAmount", "0").toString());
            String currency = (String) payload.getOrDefault("currency", "VND");

            log.info("Received order.placed for order {}, amount={} {}", orderId, amount, currency);
            paymentService.processPayment(orderId, amount, currency);

        } catch (Exception e) {
            log.error("Failed to process order.placed event: {}", e.getMessage(), e);
            throw e; // re-throw to trigger retry / DLQ
        }
    }
}

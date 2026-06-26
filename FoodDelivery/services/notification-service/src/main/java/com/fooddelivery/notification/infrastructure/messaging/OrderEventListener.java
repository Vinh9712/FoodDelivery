package com.fooddelivery.notification.infrastructure.messaging;

import com.fooddelivery.notification.application.NotificationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens for order lifecycle events from Kafka and logs notifications.
 *
 * <p>Topics consumed:
 * <ul>
 *   <li>{@code order.placed}       – new order has been placed</li>
 *   <li>{@code payment.processed}  – payment completed for an order</li>
 *   <li>{@code payment.failed}     – payment failed for an order</li>
 *   <li>{@code driver.assigned}    – a driver has been assigned to deliver the order</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final NotificationStore store;

    @KafkaListener(topics = "order.placed", groupId = "notification-service")
    public void onOrderPlaced(Map<String, Object> event) {
        String orderId = extractOrderId(event, "orderId");
        String msg = "[Order Placed] New order placed - orderId: " + orderId;
        log.info("[NOTIFICATION] {}", msg);
        store.save("ORDER_PLACED", "order.placed", msg);
    }

    @KafkaListener(topics = "payment.processed", groupId = "notification-service")
    public void onPaymentProcessed(Map<String, Object> event) {
        String orderId = extractOrderId(event, "orderId");
        String msg = "[Payment Completed] Payment completed - orderId: " + orderId;
        log.info("[NOTIFICATION] {}", msg);
        store.save("PAYMENT_PROCESSED", "payment.processed", msg);
    }

    @KafkaListener(topics = "payment.failed", groupId = "notification-service")
    public void onPaymentFailed(Map<String, Object> event) {
        String orderId = extractOrderId(event, "orderId");
        String msg = "[Payment Failed] Payment failed - orderId: " + orderId;
        log.warn("[NOTIFICATION] {}", msg);
        store.save("PAYMENT_FAILED", "payment.failed", msg);
    }

    @KafkaListener(topics = "driver.assigned", groupId = "notification-service")
    public void onDriverAssigned(Map<String, Object> event) {
        String orderId = extractOrderId(event, "orderId");
        String msg = "[Driver Assigned] Driver assigned - orderId: " + orderId;
        log.info("[NOTIFICATION] {}", msg);
        store.save("DRIVER_ASSIGNED", "driver.assigned", msg);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String extractOrderId(Map<String, Object> event, String field) {
        try {
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            if (payload != null && payload.containsKey(field)) {
                return payload.get(field).toString();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }
}

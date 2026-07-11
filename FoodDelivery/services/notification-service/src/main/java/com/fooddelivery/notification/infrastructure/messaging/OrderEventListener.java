package com.fooddelivery.notification.infrastructure.messaging;

import com.fooddelivery.notification.api.dto.NotificationRequest;
import com.fooddelivery.notification.application.NotificationJobService;
import com.fooddelivery.notification.infrastructure.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Listens for order lifecycle events and creates idempotent notification jobs.
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
@Transactional
public class OrderEventListener {

    private static final String CONSUMER_NAME = "notification-service-order-events";

    private final NotificationJobService notificationJobService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "order.placed", groupId = "notification-service")
    public void onOrderPlaced(Map<String, Object> event) {
        handle(event, "Order placed", "Your order has been received");
    }

    @KafkaListener(topics = "payment.processed", groupId = "notification-service")
    public void onPaymentProcessed(Map<String, Object> event) {
        handle(event, "Payment completed", "Your payment was completed successfully");
    }

    @KafkaListener(topics = "payment.failed", groupId = "notification-service")
    public void onPaymentFailed(Map<String, Object> event) {
        handle(event, "Payment failed", "Your payment could not be completed");
    }

    @KafkaListener(topics = "driver.assigned", groupId = "notification-service")
    public void onDriverAssigned(Map<String, Object> event) {
        handle(event, "Driver assigned", "A driver has been assigned to your order");
    }

    private void handle(Map<String, Object> event, String subject, String message) {
        UUID eventId = extractUuid(event, "eventId", false);
        if (eventId != null && processedEventRepository.existsByEventIdAndConsumer(eventId, CONSUMER_NAME)) {
            return;
        }

        UUID orderId = extractUuid(event, "orderId", true);
        UUID customerId = extractUuid(event, "customerId", true);
        if (orderId != null && customerId != null) {
            notificationJobService.enqueue(new NotificationRequest(
                    orderId, customerId, "IN_APP", subject, message));
            log.info("Persistent notification job queued: eventId={}, orderId={}, customerId={}",
                    eventId, orderId, customerId);
        } else {
            log.warn("Notification event {} is missing orderId/customerId; no job created", eventId);
        }

        if (eventId != null) {
            processedEventRepository.markProcessed(eventId, CONSUMER_NAME);
        }
    }

    @SuppressWarnings("unchecked")
    private UUID extractUuid(Map<String, Object> event, String field, boolean fromPayload) {
        try {
            Object value;
            if (fromPayload) {
                Map<String, Object> payload = (Map<String, Object>) event.get("payload");
                value = payload == null ? null : payload.get(field);
            } else {
                value = event.get(field);
            }
            return value == null ? null : UUID.fromString(value.toString());
        } catch (RuntimeException ex) {
            log.warn("Invalid UUID field {} in notification event", field);
            return null;
        }
    }
}

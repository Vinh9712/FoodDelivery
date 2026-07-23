package com.fooddelivery.notification.infrastructure.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.notification.api.dto.NotificationRequest;
import com.fooddelivery.notification.application.NotificationJobService;
import com.fooddelivery.notification.infrastructure.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consumes fulfillment family topics and creates idempotent in-app notification jobs.
 *
 * <p>Topics: {@code order.events.v1}, {@code delivery.events.v1}, {@code payment.events.v1}.
 * Payload shape is {@link IntegrationEventEnvelope} (raw JSON, no type headers).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FulfillmentEventListener {

    public static final String CONSUMER_NAME = "notification-fulfillment-v1";

    private final NotificationJobService notificationJobService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {
                    "${app.notification.kafka.order-events-topic:order.events.v1}",
                    "${app.notification.kafka.delivery-events-topic:delivery.events.v1}",
                    "${app.notification.kafka.payment-events-topic:payment.events.v1}"
            },
            groupId = "notification-service")
    @Transactional
    public void onFulfillmentEvent(String rawJson) {
        IntegrationEventEnvelope<JsonNode> envelope;
        try {
            envelope = objectMapper.readValue(rawJson, new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("Skipping unparseable fulfillment event: {}", ex.toString());
            return;
        }

        UUID eventId = envelope.eventId();
        if (processedEventRepository.existsByEventIdAndConsumer(eventId, CONSUMER_NAME)) {
            return;
        }

        NotificationCopy copy = copyFor(envelope.eventType(), envelope.payload());
        if (copy == null) {
            log.debug("Ignoring event type {} for notifications", envelope.eventType());
            processedEventRepository.markProcessed(eventId, CONSUMER_NAME);
            return;
        }

        UUID orderId = textUuid(envelope.payload(), "orderId");
        UUID customerId = textUuid(envelope.payload(), "customerId");
        if (orderId == null || customerId == null) {
            log.warn("Event {} ({}) missing orderId/customerId; no job created", eventId, envelope.eventType());
            processedEventRepository.markProcessed(eventId, CONSUMER_NAME);
            return;
        }

        notificationJobService.enqueue(new NotificationRequest(
                orderId, customerId, "IN_APP", copy.subject(), copy.message()));
        processedEventRepository.markProcessed(eventId, CONSUMER_NAME);
        log.info("Notification job queued: eventId={}, type={}, orderId={}, customerId={}",
                eventId, envelope.eventType(), orderId, customerId);
    }

    private NotificationCopy copyFor(String eventType, JsonNode payload) {
        return switch (eventType) {
            case EventContracts.ORDER_CREATED ->
                    new NotificationCopy("Order placed", "Your order has been received");
            case EventContracts.ORDER_STATUS_CHANGED -> {
                String toStatus = text(payload, "toStatus");
                yield new NotificationCopy(
                        "Order update",
                        toStatus == null ? "Your order status was updated" : "Your order is now " + toStatus);
            }
            case EventContracts.ORDER_CANCELLED ->
                    new NotificationCopy("Order cancelled", "Your order has been cancelled");
            case EventContracts.ORDER_REFUND_STATUS_CHANGED -> {
                String toRefund = text(payload, "toRefundStatus");
                if ("SUCCEEDED".equals(toRefund)) {
                    yield new NotificationCopy("Refund completed", "Your refund has been processed");
                }
                yield new NotificationCopy(
                        "Refund update",
                        toRefund == null ? "Your refund status was updated" : "Refund status: " + toRefund);
            }
            case EventContracts.DRIVER_ASSIGNED ->
                    new NotificationCopy("Driver assigned", "A driver has been assigned to your order");
            case EventContracts.DELIVERY_PICKED_UP ->
                    new NotificationCopy("Order picked up", "The driver has picked up your order");
            case EventContracts.DELIVERY_IN_TRANSIT ->
                    new NotificationCopy("Out for delivery", "Your order is on the way");
            case EventContracts.DELIVERY_COMPLETED ->
                    new NotificationCopy("Delivered", "Your order has been delivered");
            case EventContracts.DELIVERY_FAILED ->
                    new NotificationCopy("Delivery failed", "Delivery could not be completed");
            case EventContracts.PAYMENT_SUCCEEDED ->
                    new NotificationCopy("Payment completed", "Your payment was completed successfully");
            case EventContracts.PAYMENT_FAILED ->
                    new NotificationCopy("Payment failed", "Your payment could not be completed");
            case EventContracts.PAYMENT_REFUNDED ->
                    new NotificationCopy("Payment refunded", "Your payment has been refunded");
            default -> null;
        };
    }

    private static UUID textUuid(JsonNode payload, String field) {
        String value = text(payload, field);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String text(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            return null;
        }
        String value = payload.get(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private record NotificationCopy(String subject, String message) {
    }
}

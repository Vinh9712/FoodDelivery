package com.fooddelivery.payment.infrastructure.messaging;

import com.fooddelivery.payment.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes payment lifecycle events to Kafka.
 *
 * <ul>
 *   <li>{@code payment.processed} – payment completed successfully</li>
 *   <li>{@code payment.failed}    – payment could not be completed</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private static final String TOPIC_PROCESSED = "payment.processed";
    private static final String TOPIC_FAILED    = "payment.failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishProcessed(Payment payment) {
        var event = buildEvent("PAYMENT_PROCESSED", payment);
        kafkaTemplate.send(TOPIC_PROCESSED, payment.getOrderId().toString(), event);
        log.info("Published payment.processed for order {}", payment.getOrderId());
    }

    public void publishFailed(Payment payment) {
        var event = buildEvent("PAYMENT_FAILED", payment);
        kafkaTemplate.send(TOPIC_FAILED, payment.getOrderId().toString(), event);
        log.warn("Published payment.failed for order {}", payment.getOrderId());
    }

    private Map<String, Object> buildEvent(String eventType, Payment payment) {
        return Map.of(
                "eventId",   UUID.randomUUID().toString(),
                "eventType", eventType,
                "timestamp", Instant.now().toString(),
                "payload", Map.of(
                        "paymentId", payment.getId().toString(),
                        "orderId",   payment.getOrderId().toString(),
                        "status",    payment.getStatus().name(),
                        "amount",    payment.getAmount(),
                        "currency",  payment.getCurrency()
                )
        );
    }
}

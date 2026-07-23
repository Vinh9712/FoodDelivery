package com.fooddelivery.order.application.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.commonevents.payment.PaymentEventPayloads;
import com.fooddelivery.order.application.messaging.ProcessDecision;
import com.fooddelivery.order.application.messaging.SequencedConsumer;
import com.fooddelivery.order.application.messaging.SequencedEventProcessor;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.order.saga.OrderCompensationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Consumes {@code payment.events.v1} through sequenced inbox {@code order-payment-v1}.
 * {@code PaymentRefunded} confirms compensation; Succeeded/Failed advance sequence as no-ops.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentLifecycleEventListener implements SequencedConsumer {

    public static final String CONSUMER_NAME = "order-payment-v1";

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SequencedEventProcessor sequencedEventProcessor;
    private final OrderCompensationService compensationService;
    private final ObjectMapper objectMapper;

    @Override
    public String consumerName() {
        return CONSUMER_NAME;
    }

    @KafkaListener(
            topics = "${app.order.kafka.payment-events-topic:payment.events.v1}",
            groupId = "order-service-payment")
    @Transactional
    public void onEvent(String rawJson) {
        IntegrationEventEnvelope<JsonNode> envelope = sequencedEventProcessor.parseAndValidate(rawJson);
        ProcessDecision decision = sequencedEventProcessor.process(
                CONSUMER_NAME, envelope, rawJson, this::handle);
        log.debug("Payment lifecycle event {} decision={}", envelope.eventId(), decision);
    }

    @Override
    public void handle(IntegrationEventEnvelope<JsonNode> envelope) throws Exception {
        if (!"Payment".equals(envelope.aggregateType())) {
            throw new IllegalArgumentException("Unsupported payment aggregateType: " + envelope.aggregateType());
        }
        String eventType = envelope.eventType();
        JsonNode payloadNode = envelope.payload();
        switch (eventType) {
            case EventContracts.PAYMENT_SUCCEEDED -> {
                PaymentEventPayloads.PaymentSucceeded payload = objectMapper.treeToValue(
                        payloadNode, PaymentEventPayloads.PaymentSucceeded.class);
                // Sequence-only no-op: initial PAID remains REST/reconciliation-owned
                assertOrderUnchangedByPaymentBootstrap(payload.orderId());
            }
            case EventContracts.PAYMENT_FAILED -> {
                PaymentEventPayloads.PaymentFailed payload = objectMapper.treeToValue(
                        payloadNode, PaymentEventPayloads.PaymentFailed.class);
                assertOrderUnchangedByPaymentBootstrap(payload.orderId());
            }
            case EventContracts.PAYMENT_REFUNDED -> {
                PaymentEventPayloads.PaymentRefunded payload = objectMapper.treeToValue(
                        payloadNode, PaymentEventPayloads.PaymentRefunded.class);
                applyRefunded(payload, envelope.aggregateId());
            }
            default -> throw new IllegalArgumentException("Unsupported payment event type: " + eventType);
        }
    }

    private void applyRefunded(PaymentEventPayloads.PaymentRefunded payload, UUID paymentAggregateId) {
        if (!payload.paymentId().equals(paymentAggregateId)) {
            throw new IllegalArgumentException("Payment aggregateId does not match payload.paymentId");
        }
        Order order = orderRepository.findById(payload.orderId())
                .orElseThrow(() -> new OrderNotFoundException(payload.orderId()));
        BigDecimal amount = new BigDecimal(payload.amount());
        if (order.getTotalAmount().compareTo(amount) != 0
                && order.getTotalAmount().stripTrailingZeros().compareTo(amount.stripTrailingZeros()) != 0) {
            throw new IllegalArgumentException("PaymentRefunded amount does not match order total");
        }
        // Prefer compensation service for shared confirmation path (emits outbox once)
        compensationService.onRefundConfirmed(
                payload.orderId(),
                payload.paymentId(),
                payload.refundId(),
                amount,
                payload.refundedAt());
    }

    private void assertOrderUnchangedByPaymentBootstrap(UUID orderId) {
        // Load to ensure order exists; do not mutate status from PENDING via Kafka
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                log.debug("Ignoring payment bootstrap event for PENDING order {}", orderId);
            }
        });
    }
}

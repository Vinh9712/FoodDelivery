package com.fooddelivery.order.application.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.order.application.messaging.ProcessDecision;
import com.fooddelivery.order.application.messaging.SequencedEventHandler;
import com.fooddelivery.order.application.messaging.SequencedEventProcessor;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.order.saga.OrderCompensationService;
import com.fooddelivery.commonevents.order.OrderEventPayloads;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentLifecycleEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private OrderRepository orderRepository;
    private OutboxEventRepository outboxEventRepository;
    private SequencedEventProcessor sequencedEventProcessor;
    private OrderCompensationService compensationService;
    private PaymentLifecycleEventListener listener;

    private Order order;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        sequencedEventProcessor = mock(SequencedEventProcessor.class);
        compensationService = mock(OrderCompensationService.class);
        listener = new PaymentLifecycleEventListener(
                orderRepository, outboxEventRepository, sequencedEventProcessor, compensationService, objectMapper);

        order = cancellationPendingOrder();
        paymentId = UUID.randomUUID();

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        when(sequencedEventProcessor.parseAndValidate(anyString())).thenAnswer(inv ->
                realParse(inv.getArgument(0)));
        when(sequencedEventProcessor.process(anyString(), any(), anyString(), any()))
                .thenAnswer(inv -> {
                    SequencedEventHandler handler = inv.getArgument(3);
                    IntegrationEventEnvelope<JsonNode> envelope = inv.getArgument(1);
                    handler.apply(envelope);
                    return ProcessDecision.APPLIED;
                });

        doAnswer(inv -> {
            UUID orderId = inv.getArgument(0);
            Order o = orderRepository.findById(orderId).orElseThrow();
            o.confirmRefund(inv.getArgument(1), inv.getArgument(2), inv.getArgument(3), inv.getArgument(4));
            if (!o.getPendingOutboxEvents().isEmpty()) {
                outboxEventRepository.saveAll(o.getPendingOutboxEvents());
                o.clearPendingOutboxEvents();
            }
            orderRepository.save(o);
            return null;
        }).when(compensationService).onRefundConfirmed(any(), any(), any(), any(), any());
    }

    @Test
    void paymentRefundedConfirmsCancellation() {
        UUID refundId = UUID.randomUUID();
        listener.onEvent(json(refunded(UUID.randomUUID(), 1, refundId)));

        verify(sequencedEventProcessor).process(
                eq(PaymentLifecycleEventListener.CONSUMER_NAME), any(), anyString(), any());
        verify(compensationService).onRefundConfirmed(
                eq(order.getId()), eq(paymentId), eq(refundId), any(), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getRefundStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(listener.consumerName()).isEqualTo("order-payment-v1");
    }

    @Test
    void paymentSucceededIsNoOpOnOrderStatus() {
        Order pending = new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(65000));
        when(orderRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        OrderStatus before = pending.getStatus();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("paymentId", paymentId.toString());
        payload.put("orderId", pending.getId().toString());
        payload.put("customerId", pending.getCustomerId().toString());
        payload.put("amount", "65000");
        payload.put("currency", "VND");
        payload.put("paidAt", Instant.parse("2026-07-22T10:00:00Z").toString());
        listener.onEvent(json(envelope(UUID.randomUUID(), EventContracts.PAYMENT_SUCCEEDED, 1, paymentId, payload)));

        assertThat(pending.getStatus()).isEqualTo(before);
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    private IntegrationEventEnvelope<JsonNode> realParse(String raw) {
        SequencedEventProcessor real = new SequencedEventProcessor(
                mock(), mock(), mock(), objectMapper,
                java.time.Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), java.time.ZoneOffset.UTC));
        return real.parseAndValidate(raw);
    }

    private String json(ObjectNode envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ObjectNode refunded(UUID eventId, long sequence, UUID refundId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("paymentId", paymentId.toString());
        payload.put("refundId", refundId.toString());
        payload.put("orderId", order.getId().toString());
        payload.put("customerId", order.getCustomerId().toString());
        payload.put("amount", order.getTotalAmount().stripTrailingZeros().toPlainString());
        payload.put("currency", "VND");
        payload.put("refundedAt", Instant.parse("2026-07-22T11:00:00Z").toString());
        return envelope(eventId, EventContracts.PAYMENT_REFUNDED, sequence, paymentId, payload);
    }

    private ObjectNode envelope(UUID eventId, String eventType, long sequence, UUID aggregateId, ObjectNode payload) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", eventId.toString());
        root.put("eventType", eventType);
        root.put("eventVersion", 1);
        root.put("occurredAt", Instant.parse("2026-07-22T10:00:00Z").toString());
        root.put("aggregateType", "Payment");
        root.put("aggregateId", aggregateId.toString());
        root.put("aggregateSequence", sequence);
        root.set("payload", payload);
        return root;
    }

    private static Order cancellationPendingOrder() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(65000));
        order.markAsPaid();
        order.acceptByRestaurant(UUID.randomUUID());
        order.startPreparing(UUID.randomUUID());
        order.markReadyForPickup(UUID.randomUUID());
        order.beginCompensation("failed", CancellationCode.DELIVERY_FAILED,
                OrderEventPayloads.Source.DELIVERY_EVENT, Instant.parse("2026-07-22T10:30:00Z"));
        order.clearPendingOutboxEvents();
        return order;
    }
}

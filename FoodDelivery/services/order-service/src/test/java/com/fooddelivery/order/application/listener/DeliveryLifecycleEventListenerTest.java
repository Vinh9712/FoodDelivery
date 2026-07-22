package com.fooddelivery.order.application.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.order.application.messaging.ProcessDecision;
import com.fooddelivery.order.application.messaging.SequencedEventHandler;
import com.fooddelivery.order.application.messaging.SequencedEventProcessor;
import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.order.saga.OrderCompensationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryLifecycleEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private OrderRepository orderRepository;
    private OutboxEventRepository outboxEventRepository;
    private SequencedEventProcessor sequencedEventProcessor;
    private OrderCompensationService compensationService;
    private DeliveryLifecycleEventListener listener;

    private UUID orderId;
    private UUID deliveryId;
    private UUID customerId;
    private UUID driverId;
    private Order order;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        sequencedEventProcessor = mock(SequencedEventProcessor.class);
        compensationService = mock(OrderCompensationService.class);
        listener = new DeliveryLifecycleEventListener(
                orderRepository, outboxEventRepository, sequencedEventProcessor, compensationService, objectMapper);
        doAnswer(inv -> {
            Order o = orderRepository.findById(inv.getArgument(0)).orElseThrow();
            o.beginCompensation(
                    inv.getArgument(2),
                    inv.getArgument(1),
                    inv.getArgument(3),
                    Instant.parse("2026-07-22T10:15:00Z"));
            orderRepository.save(o);
            return null;
        }).when(compensationService).start(any(), any(), any(), any());

        orderId = UUID.randomUUID();
        deliveryId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        order = readyOrder(customerId);

        when(orderRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            if (id.equals(order.getId()) || id.equals(orderId)) {
                return Optional.of(order);
            }
            return Optional.empty();
        });
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // Real parse; process invokes the handler so domain mutations still run.
        when(sequencedEventProcessor.parseAndValidate(anyString())).thenAnswer(inv ->
                realParse(inv.getArgument(0)));
        when(sequencedEventProcessor.process(anyString(), any(), anyString(), any()))
                .thenAnswer(inv -> {
                    SequencedEventHandler handler = inv.getArgument(3);
                    IntegrationEventEnvelope<JsonNode> envelope = inv.getArgument(1);
                    handler.apply(envelope);
                    return ProcessDecision.APPLIED;
                });

        orderId = order.getId();
    }

    @Test
    void routesThroughSequencedProcessorWithDeliveryConsumerName() throws Exception {
        UUID completedEventId = UUID.randomUUID();

        listener.onEvent(json(driverAssigned(UUID.randomUUID(), 1)));
        listener.onEvent(json(pickedUp(UUID.randomUUID(), 2)));
        listener.onEvent(json(inTransit(UUID.randomUUID(), 3)));
        listener.onEvent(json(completed(completedEventId, 4)));

        ArgumentCaptor<String> consumerCaptor = ArgumentCaptor.forClass(String.class);
        verify(sequencedEventProcessor, org.mockito.Mockito.atLeastOnce())
                .process(consumerCaptor.capture(), any(), anyString(), any());
        assertThat(consumerCaptor.getAllValues())
                .allMatch(DeliveryLifecycleEventListener.CONSUMER_NAME::equals);

        Order restored = orderRepository.findById(orderId).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(restored.getDriverId()).isEqualTo(driverId);
        assertThat(listener.consumerName()).isEqualTo("order-delivery-v1");
    }

    @Test
    void malformedOrMissingEventIdThrowsWithoutMutation() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("eventId is required"))
                .when(sequencedEventProcessor).parseAndValidate(anyString());

        assertThatThrownBy(() -> listener.onEvent("{\"eventType\":\"DeliveryCompleted\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.READY_FOR_PICKUP);
    }

    @Test
    void deliveryFailedRequestsCancellationFromReady() {
        listener.onEvent(json(failed(UUID.randomUUID(), 1)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        verify(compensationService).start(
                eq(order.getId()),
                eq(CancellationCode.DELIVERY_FAILED),
                eq("Customer unreachable"),
                eq(OrderEventPayloads.Source.DELIVERY_EVENT));
        verify(sequencedEventProcessor).process(
                eq(DeliveryLifecycleEventListener.CONSUMER_NAME), any(), anyString(), any());
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

    private ObjectNode driverAssigned(UUID eventId, long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("deliveryId", deliveryId.toString());
        payload.put("customerId", customerId.toString());
        ObjectNode driver = payload.putObject("driver");
        driver.put("driverId", driverId.toString());
        driver.put("fullName", "Nguyen Van A");
        driver.put("phone", "0900123456");
        driver.put("vehicleType", "MOTORBIKE");
        driver.put("licensePlate", "59A1-12345");
        payload.put("assignedAt", Instant.parse("2026-07-22T10:00:00Z").toString());
        return envelope(eventId, EventContracts.DRIVER_ASSIGNED, sequence, payload);
    }

    private ObjectNode pickedUp(UUID eventId, long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("deliveryId", deliveryId.toString());
        payload.put("customerId", customerId.toString());
        payload.put("driverId", driverId.toString());
        payload.put("pickedUpAt", Instant.parse("2026-07-22T10:05:00Z").toString());
        return envelope(eventId, EventContracts.DELIVERY_PICKED_UP, sequence, payload);
    }

    private ObjectNode inTransit(UUID eventId, long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("deliveryId", deliveryId.toString());
        payload.put("customerId", customerId.toString());
        payload.put("driverId", driverId.toString());
        payload.put("deliveryStartedAt", Instant.parse("2026-07-22T10:10:00Z").toString());
        return envelope(eventId, EventContracts.DELIVERY_IN_TRANSIT, sequence, payload);
    }

    private ObjectNode completed(UUID eventId, long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("deliveryId", deliveryId.toString());
        payload.put("customerId", customerId.toString());
        payload.put("driverId", driverId.toString());
        payload.put("deliveredAt", Instant.parse("2026-07-22T10:30:00Z").toString());
        return envelope(eventId, EventContracts.DELIVERY_COMPLETED, sequence, payload);
    }

    private ObjectNode failed(UUID eventId, long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("deliveryId", deliveryId.toString());
        payload.put("customerId", customerId.toString());
        payload.put("driverId", driverId.toString());
        payload.put("failureCode", "DRIVER_REPORTED");
        payload.put("reason", "Customer unreachable");
        payload.put("failedAt", Instant.parse("2026-07-22T10:15:00Z").toString());
        return envelope(eventId, EventContracts.DELIVERY_FAILED, sequence, payload);
    }

    private ObjectNode envelope(UUID eventId, String eventType, long sequence, ObjectNode payload) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", eventId.toString());
        root.put("eventType", eventType);
        root.put("eventVersion", 1);
        root.put("occurredAt", Instant.parse("2026-07-22T10:00:00Z").toString());
        root.put("aggregateType", "Delivery");
        root.put("aggregateId", deliveryId.toString());
        root.put("aggregateSequence", sequence);
        root.set("payload", payload);
        return root;
    }

    private static Order readyOrder(UUID customerId) {
        Order order = new Order(customerId, UUID.randomUUID(), BigDecimal.valueOf(65000));
        order.markAsPaid();
        order.acceptByRestaurant(UUID.randomUUID());
        order.startPreparing(UUID.randomUUID());
        order.markReadyForPickup(UUID.randomUUID());
        order.clearPendingOutboxEvents();
        return order;
    }
}

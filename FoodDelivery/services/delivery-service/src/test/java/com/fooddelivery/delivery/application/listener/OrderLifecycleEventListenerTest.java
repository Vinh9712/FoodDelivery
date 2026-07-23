package com.fooddelivery.delivery.application.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.delivery.application.messaging.ProcessDecision;
import com.fooddelivery.delivery.application.messaging.SequencedEventHandler;
import com.fooddelivery.delivery.application.messaging.SequencedOrderEventProcessor;
import com.fooddelivery.delivery.application.service.DeliveryLifecycleService;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import com.fooddelivery.delivery.domain.model.valueobject.VehicleType;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderLifecycleEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private SequencedOrderEventProcessor sequencedEventProcessor;
    private DeliveryLifecycleService lifecycleService;
    private SimpleMeterRegistry meterRegistry;
    private OrderLifecycleEventListener listener;

    private Delivery delivery;
    private Driver driver;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        sequencedEventProcessor = mock(SequencedOrderEventProcessor.class);
        lifecycleService = mock(DeliveryLifecycleService.class);
        meterRegistry = new SimpleMeterRegistry();
        listener = new OrderLifecycleEventListener(
                sequencedEventProcessor, lifecycleService, objectMapper, provider(meterRegistry));

        orderId = UUID.randomUUID();
        delivery = new Delivery(orderId);
        driver = new Driver("Shipper", "0900000001", VehicleType.MOTORBIKE, "59A1-1", BigDecimal.valueOf(4.5));
        driver.goOnline();

        when(sequencedEventProcessor.parseAndValidate(anyString())).thenAnswer(inv -> realParse(inv.getArgument(0)));
        when(sequencedEventProcessor.process(anyString(), any(), anyString(), any()))
                .thenAnswer(inv -> {
                    SequencedEventHandler handler = inv.getArgument(3);
                    IntegrationEventEnvelope<JsonNode> envelope = inv.getArgument(1);
                    handler.apply(envelope);
                    return ProcessDecision.APPLIED;
                });
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"PENDING", "FINDING_DRIVER", "DRIVER_ASSIGNED"})
    void cancellationBeforePickupCancelsAndReleasesDriver(DeliveryStatus initial) {
        seedStatus(initial);
        when(lifecycleService.cancelFromOrder(eq(orderId), anyString())).thenAnswer(inv -> {
            Delivery.CancelFromOrderResult result = delivery.cancelFromOrder(inv.getArgument(1), Instant.now());
            return result;
        });

        listener.onEvent(json(orderCancelled(1)));

        verify(lifecycleService).cancelFromOrder(eq(orderId), anyString());
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        if (initial == DeliveryStatus.DRIVER_ASSIGNED) {
            assertThat(delivery.cancelFromOrder("again", Instant.now()).releaseDriver()).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"PICKED_UP", "DELIVERING", "DELIVERED"})
    void cancellationAfterPickupDoesNotRewriteDelivery(DeliveryStatus initial) {
        seedStatus(initial);
        DeliveryStatus before = delivery.getStatus();
        when(lifecycleService.cancelFromOrder(eq(orderId), anyString())).thenAnswer(inv -> {
            Delivery.CancelFromOrderResult result = delivery.cancelFromOrder(inv.getArgument(1), Instant.now());
            if (result.afterPickup()) {
                meterRegistry.counter(OrderLifecycleEventListener.AFTER_PICKUP_METRIC).increment();
            }
            return result;
        });

        // Listener also increments metric when afterPickup — so mock returns afterPickup and listener increments
        when(lifecycleService.cancelFromOrder(eq(orderId), anyString()))
                .thenReturn(Delivery.CancelFromOrderResult.afterPickup(initial));

        listener.onEvent(json(orderCancelled(1)));

        assertThat(delivery.getStatus()).isEqualTo(before);
        assertThat(meterRegistry.counter(OrderLifecycleEventListener.AFTER_PICKUP_METRIC).count()).isEqualTo(1.0);
    }

    @Test
    void orderCancelledWithNoDeliveryIsNoOp() {
        when(lifecycleService.cancelFromOrder(eq(orderId), anyString()))
                .thenReturn(Delivery.CancelFromOrderResult.alreadyCancelled());

        listener.onEvent(json(orderCancelled(1)));

        verify(lifecycleService).cancelFromOrder(eq(orderId), anyString());
        assertThat(meterRegistry.find(OrderLifecycleEventListener.AFTER_PICKUP_METRIC).counter()).isNull();
    }

    @Test
    void orderCreatedAdvancesAsNoOp() {
        listener.onEvent(json(orderCreated(1)));
        verify(lifecycleService, never()).cancelFromOrder(any(), any());
    }

    @Test
    void orderStatusChangedAdvancesAsNoOp() {
        listener.onEvent(json(orderStatusChanged(2)));
        verify(lifecycleService, never()).cancelFromOrder(any(), any());
    }

    private void seedStatus(DeliveryStatus status) {
        switch (status) {
            case PENDING -> {
            }
            case FINDING_DRIVER -> delivery.startFindingDriver();
            case DRIVER_ASSIGNED -> {
                delivery.startFindingDriver();
                delivery.assignDriver(driver.getId(), Instant.parse("2026-07-22T12:00:00Z"));
            }
            case PICKED_UP -> {
                delivery.startFindingDriver();
                delivery.assignDriver(driver.getId(), Instant.parse("2026-07-22T12:00:00Z"));
                delivery.pickUp(Instant.parse("2026-07-22T12:10:00Z"));
            }
            case DELIVERING -> {
                delivery.startFindingDriver();
                delivery.assignDriver(driver.getId(), Instant.parse("2026-07-22T12:00:00Z"));
                delivery.pickUp(Instant.parse("2026-07-22T12:10:00Z"));
                delivery.startDelivering(Instant.parse("2026-07-22T12:15:00Z"));
            }
            case DELIVERED -> {
                delivery.startFindingDriver();
                delivery.assignDriver(driver.getId(), Instant.parse("2026-07-22T12:00:00Z"));
                delivery.pickUp(Instant.parse("2026-07-22T12:10:00Z"));
                delivery.startDelivering(Instant.parse("2026-07-22T12:15:00Z"));
                delivery.complete(Instant.parse("2026-07-22T12:40:00Z"));
            }
            default -> throw new IllegalStateException(status.name());
        }
    }

    private IntegrationEventEnvelope<JsonNode> realParse(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        return new IntegrationEventEnvelope<>(
                UUID.fromString(root.get("eventId").asText()),
                root.get("eventType").asText(),
                root.get("eventVersion").asInt(),
                Instant.parse(root.get("occurredAt").asText()),
                root.get("aggregateType").asText(),
                UUID.fromString(root.get("aggregateId").asText()),
                root.get("aggregateSequence").asLong(),
                root.get("payload"));
    }

    private String json(ObjectNode envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ObjectNode orderCancelled(long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("customerId", UUID.randomUUID().toString());
        payload.put("restaurantId", UUID.randomUUID().toString());
        payload.put("cancellationCode", "RESTAURANT_ACCEPTANCE_TIMEOUT");
        payload.put("reason", "timeout");
        payload.put("paymentStatus", "REFUNDED");
        payload.put("refundStatus", "SUCCEEDED");
        payload.put("cancelledAt", Instant.parse("2026-07-22T12:30:00Z").toString());
        return envelope(EventContracts.ORDER_CANCELLED, sequence, payload);
    }

    private ObjectNode orderCreated(long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("customerId", UUID.randomUUID().toString());
        payload.put("restaurantId", UUID.randomUUID().toString());
        payload.put("totalAmount", "100000");
        payload.put("currency", "VND");
        payload.put("createdAt", Instant.parse("2026-07-22T12:00:00Z").toString());
        return envelope(EventContracts.ORDER_CREATED, sequence, payload);
    }

    private ObjectNode orderStatusChanged(long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("customerId", UUID.randomUUID().toString());
        payload.put("restaurantId", UUID.randomUUID().toString());
        payload.put("fromStatus", "PAID");
        payload.put("toStatus", "CONFIRMED");
        payload.put("source", "RESTAURANT");
        payload.put("changedAt", Instant.parse("2026-07-22T12:05:00Z").toString());
        return envelope(EventContracts.ORDER_STATUS_CHANGED, sequence, payload);
    }

    private ObjectNode envelope(String eventType, long sequence, ObjectNode payload) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", UUID.randomUUID().toString());
        root.put("eventType", eventType);
        root.put("eventVersion", 1);
        root.put("occurredAt", Instant.parse("2026-07-22T12:30:00Z").toString());
        root.put("aggregateType", "Order");
        root.put("aggregateId", orderId.toString());
        root.put("aggregateSequence", sequence);
        root.set("payload", payload);
        return root;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfAvailable(Supplier<T> defaultSupplier) {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getIfUnique(Supplier<T> defaultSupplier) {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }
}

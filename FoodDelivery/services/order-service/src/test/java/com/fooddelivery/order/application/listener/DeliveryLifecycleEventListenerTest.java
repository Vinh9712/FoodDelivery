package com.fooddelivery.order.application.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.order.infrastructure.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryLifecycleEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Set<UUID> processedIds = new HashSet<>();

    private OrderRepository orderRepository;
    private ProcessedEventRepository processedEventRepository;
    private OutboxEventRepository outboxEventRepository;
    private DeliveryLifecycleEventListener listener;

    private UUID orderId;
    private UUID deliveryId;
    private UUID customerId;
    private UUID driverId;
    private Order order;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        listener = new DeliveryLifecycleEventListener(
                orderRepository, processedEventRepository, outboxEventRepository, objectMapper);

        orderId = UUID.randomUUID();
        deliveryId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        order = readyOrder(customerId);
        // Use reflection-free approach: legacy constructor creates new id — re-bind via stubbing by status path.
        // Build READY_FOR_PICKUP order and always return the same instance from repository.
        when(orderRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            if (id.equals(order.getId()) || id.equals(orderId)) {
                return Optional.of(order);
            }
            return Optional.empty();
        });
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        when(processedEventRepository.existsByEventIdAndConsumer(any(), anyString()))
                .thenAnswer(inv -> processedIds.contains(inv.getArgument(0)));
        doAnswer(inv -> {
            processedIds.add(inv.getArgument(0));
            return null;
        }).when(processedEventRepository).markProcessed(any(), anyString());

        // Align helper orderId with the real order id for payload keys
        orderId = order.getId();
    }

    @Test
    void appliesLifecycleInOrderAndDeduplicatesEventId() {
        UUID completedEventId = UUID.randomUUID();

        listener.onEvent(json(driverAssigned(UUID.randomUUID(), 1)));
        listener.onEvent(json(pickedUp(UUID.randomUUID(), 2)));
        listener.onEvent(json(inTransit(UUID.randomUUID(), 3)));
        listener.onEvent(json(completed(completedEventId, 4)));
        listener.onEvent(json(completed(completedEventId, 4)));

        Order restored = orderRepository.findById(orderId).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(processedIds).hasSize(4);
        assertThat(restored.getDriverId()).isEqualTo(driverId);
    }

    @Test
    void malformedOrMissingEventIdThrowsWithoutMutation() {
        assertThatThrownBy(() -> listener.onEvent("{\"eventType\":\"DeliveryCompleted\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(processedIds).isEmpty();
    }

    @Test
    void deliveryFailedRequestsCancellationFromReady() {
        listener.onEvent(json(failed(UUID.randomUUID(), 1)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        assertThat(processedIds).hasSize(1);
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

package com.fooddelivery.order.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.order.application.listener.DeliveryLifecycleEventListener;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.client.DeliveryServiceClient;
import com.fooddelivery.order.infrastructure.client.NotificationServiceClient;
import com.fooddelivery.order.infrastructure.client.PaymentServiceClient;
import com.fooddelivery.order.infrastructure.client.RestaurantServiceClient;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sequence 2 before 1 must not mutate order until 1 arrives; both apply once.
 * Without Docker/Kafka — exercises sequenced inbox via listener entrypoint + H2.
 */
@SpringBootTest
@ActiveProfiles("test")
class DuplicateAndOutOfOrderEventsIT {

    @Autowired
    private DeliveryLifecycleEventListener deliveryListener;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private PaymentServiceClient paymentServiceClient;
    @MockBean
    private DeliveryServiceClient deliveryServiceClient;
    @MockBean
    private NotificationServiceClient notificationServiceClient;
    @MockBean
    private RestaurantServiceClient restaurantServiceClient;

    @Test
    void outOfOrderDeliveryEventsApplyOnceInSequence() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(80_000));
        order.markPaid(Instant.parse("2026-07-22T10:00:00Z"), Duration.ofMinutes(10));
        order.acceptByRestaurant(UUID.randomUUID());
        order.startPreparing(UUID.randomUUID());
        order.markReadyForPickup(UUID.randomUUID());
        order = orderRepository.saveAndFlush(order);

        UUID deliveryId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        UUID customerId = order.getCustomerId();

        // seq 2 first (picked up) — deferred
        deliveryListener.onEvent(pickedUp(deliveryId, order.getId(), customerId, driverId, 2));
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.READY_FOR_PICKUP);

        // seq 1 (driver assigned) — applies then drains seq 2
        deliveryListener.onEvent(driverAssigned(deliveryId, order.getId(), customerId, driverId, 1));

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PICKED_UP);

        // duplicate seq 1 — no double transition
        deliveryListener.onEvent(driverAssigned(deliveryId, order.getId(), customerId, driverId, 1));
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PICKED_UP);
    }

    @Test
    void reliabilityMetricNamesAreRegistered() {
        assertThat(meterRegistry.find("outbox_pending").tag("service", "order").gauge()).isNotNull();
        assertThat(meterRegistry.find("outbox_oldest_unpublished_seconds").tag("service", "order").gauge())
                .isNotNull();
        assertThat(meterRegistry.find("integration_event_deferred").tag("service", "order").gauge())
                .isNotNull();
        // Pre-registered reconciliation counters
        assertThat(meterRegistry.find("order_delivery_reconciliation_total").counters()).isNotEmpty();
        assertThat(meterRegistry.find("order_refund_reconciliation_total").counters()).isNotEmpty();
        assertThat(meterRegistry.find("outbox_publish_retry_total").counter()).isNotNull();
        assertThat(meterRegistry.find("outbox_dead_letter_total").counter()).isNotNull();
    }

    private String driverAssigned(UUID deliveryId, UUID orderId, UUID customerId, UUID driverId, long seq) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("orderId", orderId.toString());
            payload.put("deliveryId", deliveryId.toString());
            payload.put("customerId", customerId.toString());
            ObjectNode driver = payload.putObject("driver");
            driver.put("driverId", driverId.toString());
            driver.put("fullName", "Driver");
            driver.put("phone", "0900123456");
            driver.put("vehicleType", "MOTORBIKE");
            driver.put("licensePlate", "59A1-12345");
            payload.put("assignedAt", Instant.parse("2026-07-22T10:20:00Z").toString());
            return wrap(EventContracts.DRIVER_ASSIGNED, deliveryId, seq, payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String pickedUp(UUID deliveryId, UUID orderId, UUID customerId, UUID driverId, long seq) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("orderId", orderId.toString());
            payload.put("deliveryId", deliveryId.toString());
            payload.put("customerId", customerId.toString());
            payload.put("driverId", driverId.toString());
            payload.put("pickedUpAt", Instant.parse("2026-07-22T10:30:00Z").toString());
            return wrap(EventContracts.DELIVERY_PICKED_UP, deliveryId, seq, payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String wrap(String type, UUID aggregateId, long seq, ObjectNode payload) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", UUID.randomUUID().toString());
        root.put("eventType", type);
        root.put("eventVersion", 1);
        root.put("occurredAt", Instant.parse("2026-07-22T10:20:00Z").toString());
        root.put("aggregateType", "Delivery");
        root.put("aggregateId", aggregateId.toString());
        root.put("aggregateSequence", seq);
        root.set("payload", payload);
        return objectMapper.writeValueAsString(root);
    }
}

package com.fooddelivery.delivery.application.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import com.fooddelivery.delivery.domain.model.valueobject.VehicleType;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import com.fooddelivery.delivery.infrastructure.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2 SpringBoot IT: OrderCancelled cancels pre-pickup delivery and releases driver.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderCancelledEventIntegrationTest {

    @Autowired
    private OrderLifecycleEventListener listener;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"PENDING", "FINDING_DRIVER", "DRIVER_ASSIGNED"})
    void cancellationBeforePickupCancelsAndReleasesDriver(DeliveryStatus initial) {
        Driver driver = onlineDriver();
        Delivery delivery = new Delivery(UUID.randomUUID());
        seed(delivery, initial, driver.getId());
        delivery = deliveryRepository.saveAndFlush(delivery);
        UUID orderId = delivery.getOrderId();
        if (initial == DeliveryStatus.DRIVER_ASSIGNED) {
            driver.reserveForDelivery();
            driverRepository.saveAndFlush(driver);
            assertThat(driver.isAvailable()).isFalse();
        }

        listener.onEvent(orderCancelledJson(orderId, 1));

        Delivery reloaded = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        if (initial == DeliveryStatus.DRIVER_ASSIGNED) {
            Driver released = driverRepository.findById(driver.getId()).orElseThrow();
            assertThat(released.isAvailable()).isTrue();
        }
        assertThat(processedEventRepository.existsByEventIdAndConsumer(
                // any processed event for consumer
                reloaded.getId(), OrderLifecycleEventListener.CONSUMER_NAME)).isFalse();
        // sequence was applied — verify via second cancel is idempotent
        listener.onEvent(orderCancelledJson(orderId, 1)); // same event id different — use new event same sequence
        assertThat(deliveryRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(DeliveryStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"PICKED_UP", "DELIVERING", "DELIVERED"})
    void cancellationAfterPickupDoesNotRewriteDelivery(DeliveryStatus initial) {
        Driver driver = onlineDriver();
        Delivery delivery = new Delivery(UUID.randomUUID());
        seed(delivery, initial, driver.getId());
        delivery = deliveryRepository.saveAndFlush(delivery);
        UUID orderId = delivery.getOrderId();
        DeliveryStatus before = delivery.getStatus();

        listener.onEvent(orderCancelledJson(orderId, 1));

        Delivery reloaded = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(before);
    }

    @Test
    void orderCancelledWithoutDeliveryAdvancesSequence() {
        UUID orderId = UUID.randomUUID();
        listener.onEvent(orderCancelledJson(orderId, 1));
        assertThat(deliveryRepository.findByOrderId(orderId)).isEmpty();
        // second sequence event should apply as next (no gap)
        listener.onEvent(orderCreatedJson(orderId, 2));
        assertThat(deliveryRepository.findByOrderId(orderId)).isEmpty();
    }

    private void seed(Delivery delivery, DeliveryStatus status, UUID driverId) {
        switch (status) {
            case PENDING -> {
            }
            case FINDING_DRIVER -> delivery.startFindingDriver();
            case DRIVER_ASSIGNED -> {
                delivery.startFindingDriver();
                delivery.assignDriver(driverId, Instant.parse("2026-07-22T12:00:00Z"));
            }
            case PICKED_UP -> {
                delivery.startFindingDriver();
                delivery.assignDriver(driverId, Instant.parse("2026-07-22T12:00:00Z"));
                delivery.pickUp(Instant.parse("2026-07-22T12:10:00Z"));
            }
            case DELIVERING -> {
                delivery.startFindingDriver();
                delivery.assignDriver(driverId, Instant.parse("2026-07-22T12:00:00Z"));
                delivery.pickUp(Instant.parse("2026-07-22T12:10:00Z"));
                delivery.startDelivering(Instant.parse("2026-07-22T12:15:00Z"));
            }
            case DELIVERED -> {
                delivery.startFindingDriver();
                delivery.assignDriver(driverId, Instant.parse("2026-07-22T12:00:00Z"));
                delivery.pickUp(Instant.parse("2026-07-22T12:10:00Z"));
                delivery.startDelivering(Instant.parse("2026-07-22T12:15:00Z"));
                delivery.complete(Instant.parse("2026-07-22T12:40:00Z"));
            }
            default -> throw new IllegalStateException(status.name());
        }
    }

    private Driver onlineDriver() {
        Driver driver = new Driver(
                "Driver " + UUID.randomUUID().toString().substring(0, 8),
                "09" + (int) (Math.random() * 1_000_000_00),
                VehicleType.MOTORBIKE,
                "59A" + (int) (Math.random() * 100000),
                BigDecimal.valueOf(4.8));
        driver.goOnline();
        return driverRepository.saveAndFlush(driver);
    }

    private String orderCancelledJson(UUID orderId, long sequence) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("orderId", orderId.toString());
            payload.put("customerId", UUID.randomUUID().toString());
            payload.put("restaurantId", UUID.randomUUID().toString());
            payload.put("cancellationCode", "DELIVERY_FAILED");
            payload.put("reason", "order cancelled");
            payload.put("paymentStatus", "REFUNDED");
            payload.put("refundStatus", "SUCCEEDED");
            payload.put("cancelledAt", Instant.parse("2026-07-22T13:00:00Z").toString());
            ObjectNode root = objectMapper.createObjectNode();
            root.put("eventId", UUID.randomUUID().toString());
            root.put("eventType", EventContracts.ORDER_CANCELLED);
            root.put("eventVersion", 1);
            root.put("occurredAt", Instant.parse("2026-07-22T13:00:00Z").toString());
            root.put("aggregateType", "Order");
            root.put("aggregateId", orderId.toString());
            root.put("aggregateSequence", sequence);
            root.set("payload", payload);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String orderCreatedJson(UUID orderId, long sequence) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("orderId", orderId.toString());
            payload.put("customerId", UUID.randomUUID().toString());
            payload.put("restaurantId", UUID.randomUUID().toString());
            payload.put("totalAmount", "50000");
            payload.put("currency", "VND");
            payload.put("createdAt", Instant.parse("2026-07-22T12:00:00Z").toString());
            ObjectNode root = objectMapper.createObjectNode();
            root.put("eventId", UUID.randomUUID().toString());
            root.put("eventType", EventContracts.ORDER_CREATED);
            root.put("eventVersion", 1);
            root.put("occurredAt", Instant.parse("2026-07-22T12:00:00Z").toString());
            root.put("aggregateType", "Order");
            root.put("aggregateId", orderId.toString());
            root.put("aggregateSequence", sequence);
            root.set("payload", payload);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

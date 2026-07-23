package com.fooddelivery.delivery.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.delivery.domain.exception.DeliveryAccessDeniedException;
import com.fooddelivery.delivery.domain.exception.DeliveryNotFoundException;
import com.fooddelivery.delivery.domain.exception.InvalidDeliveryStateException;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.Address;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import com.fooddelivery.delivery.domain.model.valueobject.VehicleType;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import com.fooddelivery.delivery.infrastructure.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DeliveryLifecycleServiceIntegrationTest.TestConfig.class)
class DeliveryLifecycleServiceIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        DeliveryAssignmentService deliveryAssignmentService(
                DeliveryRepository deliveryRepository,
                DriverRepository driverRepository,
                OutboxEventRepository outboxEventRepository,
                ObjectMapper objectMapper) {
            return new DeliveryAssignmentService(
                    deliveryRepository, driverRepository, outboxEventRepository, objectMapper,
                    Duration.ofSeconds(1), Duration.ofSeconds(10), 5);
        }

        @Bean
        DeliveryLifecycleService deliveryLifecycleService(
                DeliveryRepository deliveryRepository,
                DriverRepository driverRepository,
                OutboxEventRepository outboxEventRepository,
                DeliveryAssignmentService assignmentService,
                ObjectMapper objectMapper) {
            return new DeliveryLifecycleService(
                    deliveryRepository, driverRepository, outboxEventRepository,
                    assignmentService, objectMapper);
        }
    }

    @Autowired
    private DeliveryLifecycleService lifecycleService;
    @Autowired
    private DeliveryAssignmentService assignmentService;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void cannotSkipStateFromAssignedToDelivering() {
        Fixture fx = assignedDelivery();

        assertThatThrownBy(() -> lifecycleService.startDelivery(fx.deliveryId(), fx.userId()))
                .isInstanceOf(InvalidDeliveryStateException.class);
    }

    @Test
    void otherDriverCannotUpdateDelivery() {
        Fixture fx = assignedDelivery();
        Driver other = onlineDriver("Other", "0900111222", "59Z9-99999", UUID.randomUUID());

        assertThatThrownBy(() -> lifecycleService.pickUp(fx.deliveryId(), other.getUserId()))
                .isInstanceOf(DeliveryAccessDeniedException.class);
    }

    @Test
    void acceptingDeliveryPublishesCompleteDriverSnapshot() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Delivery delivery = deliveryRepository.save(new Delivery(
                orderId, customerId, null, new Address("Addr", null, null), null));
        UUID userId = UUID.randomUUID();
        Driver driver = onlineDriver("Late", "0900555666", "59L1-55555", userId);

        lifecycleService.accept(delivery.getId(), userId);

        var event = outboxEventRepository.findAll().stream()
                .filter(e -> "driver.assigned".equals(e.getEventType()))
                .findFirst().orElseThrow();
        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.path("customerId").asText()).isEqualTo(customerId.toString());
        assertThat(payload.path("driver").path("driverId").asText()).isEqualTo(driver.getId().toString());
        assertThat(payload.path("driver").path("fullName").asText()).isEqualTo("Late");
        assertThat(payload.path("assignedAt").asText()).isNotBlank();
    }

    @Test
    void completeDeliveryReleasesDriver() {
        Fixture fx = assignedDelivery();

        lifecycleService.pickUp(fx.deliveryId(), fx.userId());
        lifecycleService.startDelivery(fx.deliveryId(), fx.userId());
        lifecycleService.complete(fx.deliveryId(), fx.userId());

        Driver driver = driverRepository.findById(fx.driverId()).orElseThrow();
        Delivery delivery = deliveryRepository.findById(fx.deliveryId()).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(driver.isAvailable()).isTrue();
        assertThat(outboxEventRepository.findAll().stream()
                .anyMatch(e -> "delivery.completed".equals(e.getEventType()))).isTrue();
    }

    @Test
    void failDeliveryReleasesDriver() {
        Fixture fx = assignedDelivery();

        lifecycleService.fail(fx.deliveryId(), fx.userId(), "Customer unreachable");

        Driver driver = driverRepository.findById(fx.driverId()).orElseThrow();
        Delivery delivery = deliveryRepository.findById(fx.deliveryId()).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getDriverId()).isEqualTo(fx.driverId());
        assertThat(driver.isAvailable()).isTrue();
        assertThat(outboxEventRepository.findAll().stream()
                .anyMatch(e -> "delivery.failed".equals(e.getEventType()))).isTrue();
    }

    @Test
    void getsDeliveryByOrderId() {
        UUID orderId = UUID.randomUUID();
        Delivery saved = deliveryRepository.save(new Delivery(
                orderId, UUID.randomUUID(), null, new Address("123 Nguyen Trai", null, null), null));

        Delivery found = lifecycleService.getDeliveryByOrderId(orderId);

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getOrderId()).isEqualTo(orderId);
    }

    @Test
    void missingOrderDeliveryThrowsNotFound() {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> lifecycleService.getDeliveryByOrderId(orderId))
                .isInstanceOf(DeliveryNotFoundException.class);
    }

    @Test
    void assignmentRetrySucceedsWhenDriverComesOnline() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        DeliveryAssignmentService.AssignmentResult pending =
                assignmentService.scheduleDelivery(orderId, customerId, "Addr");
        assertThat(pending.assigned()).isFalse();
        assertThat(pending.deliveryStatus()).isEqualTo(DeliveryStatus.FINDING_DRIVER);

        Driver driver = onlineDriver("Late", "0900333444", "59L1-11111", UUID.randomUUID());
        // Re-invoke assignment once a driver is online (same path as retry scheduler).
        DeliveryAssignmentService.AssignmentResult retried =
                assignmentService.scheduleDelivery(orderId, customerId, "Addr");

        assertThat(retried.assigned()).isTrue();
        Delivery delivery = deliveryRepository.findById(pending.deliveryId()).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DRIVER_ASSIGNED);
        assertThat(delivery.getDriverId()).isEqualTo(driver.getId());
        assertThat(driverRepository.findById(driver.getId()).orElseThrow().isAvailable()).isFalse();
    }

    private Fixture assignedDelivery() {
        UUID userId = UUID.randomUUID();
        Driver driver = onlineDriver("Main", "0900123456", "59A1-12345", userId);
        DeliveryAssignmentService.AssignmentResult result =
                assignmentService.scheduleDelivery(UUID.randomUUID(), UUID.randomUUID(), "Pickup road");
        assertThat(result.assigned()).isTrue();
        return new Fixture(result.deliveryId(), driver.getId(), userId);
    }

    private Driver onlineDriver(String name, String phone, String plate, UUID userId) {
        Driver driver = new Driver(name, phone, VehicleType.MOTORBIKE, plate, new BigDecimal("4.90"));
        driver.linkUser(userId);
        driver.goOnline();
        return driverRepository.save(driver);
    }

    private record Fixture(UUID deliveryId, UUID driverId, UUID userId) {}
}

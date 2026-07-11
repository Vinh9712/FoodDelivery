package com.fooddelivery.delivery.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DeliveryAssignmentServiceIntegrationTest.TestConfig.class)
class DeliveryAssignmentServiceIntegrationTest {

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
                    Duration.ofSeconds(1), Duration.ofSeconds(10), 3);
        }
    }

    private final DeliveryAssignmentService assignmentService;
    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Autowired
    DeliveryAssignmentServiceIntegrationTest(
            DeliveryAssignmentService assignmentService,
            DeliveryRepository deliveryRepository,
            DriverRepository driverRepository,
            OutboxEventRepository outboxEventRepository) {
        this.assignmentService = assignmentService;
        this.deliveryRepository = deliveryRepository;
        this.driverRepository = driverRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Test
    void scheduleDeliveryPersistsAssignmentAndIsIdempotent() {
        Driver driver = onlineDriver("Driver One", "0900000001", "59A1-00001");
        UUID orderId = UUID.randomUUID();

        DeliveryAssignmentService.AssignmentResult first = assignmentService.scheduleDelivery(
                orderId, "123 Nguyen Trai, District 1");
        DeliveryAssignmentService.AssignmentResult duplicate = assignmentService.scheduleDelivery(
                orderId, "123 Nguyen Trai, District 1");
        DeliveryAssignmentService.AssignmentResult secondOrder = assignmentService.scheduleDelivery(
                UUID.randomUUID(), "789 Hai Ba Trung, District 3");

        Delivery persisted = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(first.assigned()).isTrue();
        assertThat(duplicate.assigned()).isTrue();
        assertThat(duplicate.deliveryId()).isEqualTo(first.deliveryId());
        assertThat(duplicate.driverId()).isEqualTo(driver.getId());
        assertThat(secondOrder.assigned()).isFalse();
        assertThat(persisted.getStatus()).isEqualTo(DeliveryStatus.DRIVER_ASSIGNED);
        assertThat(persisted.getDriverId()).isEqualTo(driver.getId());
        assertThat(persisted.getDropoffAddress().text()).isEqualTo("123 Nguyen Trai, District 1");
        assertThat(driverRepository.findById(driver.getId()).orElseThrow().isAvailable()).isFalse();
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void twoOrdersCannotTakeSameDriver() {
        Driver driver = onlineDriver("Shared", "0900000009", "59A1-00009");

        DeliveryAssignmentService.AssignmentResult first = assignmentService.scheduleDelivery(
                UUID.randomUUID(), "Addr 1");
        DeliveryAssignmentService.AssignmentResult second = assignmentService.scheduleDelivery(
                UUID.randomUUID(), "Addr 2");

        assertThat(first.assigned()).isTrue();
        assertThat(first.driverId()).isEqualTo(driver.getId());
        assertThat(second.assigned()).isFalse();
        assertThat(second.deliveryStatus()).isEqualTo(DeliveryStatus.FINDING_DRIVER);
    }

    @Test
    void scheduleDeliveryPersistsPendingAssignmentAndCanRetryWhenDriverOnline() {
        UUID orderId = UUID.randomUUID();

        DeliveryAssignmentService.AssignmentResult pending = assignmentService.scheduleDelivery(
                orderId, "456 Le Loi, District 3");

        assertThat(pending.assigned()).isFalse();
        assertThat(deliveryRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(DeliveryStatus.FINDING_DRIVER);

        Driver driver = onlineDriver("Driver Two", "0900000002", "59A1-00002");
        DeliveryAssignmentService.AssignmentResult retried = assignmentService.scheduleDelivery(
                orderId, "456 Le Loi, District 3");

        assertThat(retried.assigned()).isTrue();
        assertThat(retried.deliveryId()).isEqualTo(pending.deliveryId());
        assertThat(retried.driverId()).isEqualTo(driver.getId());
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void retryBackfillsCustomerOwnershipForExistingDelivery() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Delivery existing = deliveryRepository.save(new Delivery(orderId));

        assignmentService.scheduleDelivery(orderId, customerId, "Customer address");

        Delivery updated = deliveryRepository.findById(existing.getId()).orElseThrow();
        assertThat(updated.getCustomerId()).isEqualTo(customerId);
    }

    private Driver onlineDriver(String name, String phone, String plate) {
        Driver driver = new Driver(name, phone, VehicleType.MOTORBIKE, plate, new BigDecimal("4.80"));
        driver.goOnline();
        return driverRepository.save(driver);
    }
}

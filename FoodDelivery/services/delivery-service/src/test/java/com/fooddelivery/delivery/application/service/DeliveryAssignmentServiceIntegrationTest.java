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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DeliveryAssignmentService.class, DeliveryAssignmentServiceIntegrationTest.TestConfig.class})
class DeliveryAssignmentServiceIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
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
        Driver driver = driverRepository.save(new Driver(
                "Driver One", "0900000001", VehicleType.MOTORBIKE,
                "59A1-00001", new BigDecimal("4.80")));
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
    void scheduleDeliveryPersistsPendingAssignmentAndCanRetry() {
        UUID orderId = UUID.randomUUID();

        DeliveryAssignmentService.AssignmentResult pending = assignmentService.scheduleDelivery(
                orderId, "456 Le Loi, District 3");

        assertThat(pending.assigned()).isFalse();
        assertThat(deliveryRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(DeliveryStatus.FINDING_DRIVER);

        Driver driver = driverRepository.save(new Driver(
                "Driver Two", "0900000002", VehicleType.MOTORBIKE,
                "59A1-00002", new BigDecimal("4.70")));
        DeliveryAssignmentService.AssignmentResult retried = assignmentService.scheduleDelivery(
                orderId, "456 Le Loi, District 3");

        assertThat(retried.assigned()).isTrue();
        assertThat(retried.deliveryId()).isEqualTo(pending.deliveryId());
        assertThat(retried.driverId()).isEqualTo(driver.getId());
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }
}

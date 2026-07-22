package com.fooddelivery.delivery.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.delivery.api.dto.DeliveryRequest;
import com.fooddelivery.delivery.domain.exception.DeliveryNotFoundException;
import com.fooddelivery.delivery.domain.exception.DeliveryScheduleConflictException;
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
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        DeliveryAssignmentService deliveryAssignmentService(
                DeliveryRepository deliveryRepository,
                DriverRepository driverRepository,
                OutboxEventRepository outboxEventRepository,
                ObjectMapper objectMapper,
                Clock clock) {
            return new DeliveryAssignmentService(
                    deliveryRepository, driverRepository, outboxEventRepository, objectMapper, clock,
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
        DeliveryRequest request = request(orderId, "12 Le Loi", "123 Nguyen Trai");
        String key = key(orderId);

        DeliveryAssignmentService.AssignmentResult first = assignmentService.scheduleDelivery(key, request);
        DeliveryAssignmentService.AssignmentResult duplicate = assignmentService.scheduleDelivery(key, request);
        UUID secondOrderId = UUID.randomUUID();
        DeliveryAssignmentService.AssignmentResult secondOrder = assignmentService.scheduleDelivery(
                key(secondOrderId), request(secondOrderId, "99 Other", "789 Hai Ba Trung"));

        Delivery persisted = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(first.assigned()).isTrue();
        assertThat(duplicate.assigned()).isTrue();
        assertThat(duplicate.deliveryId()).isEqualTo(first.deliveryId());
        assertThat(duplicate.driverId()).isEqualTo(driver.getId());
        assertThat(secondOrder.assigned()).isFalse();
        assertThat(persisted.getStatus()).isEqualTo(DeliveryStatus.DRIVER_ASSIGNED);
        assertThat(persisted.getDriverId()).isEqualTo(driver.getId());
        assertThat(persisted.getDropoffAddress().text()).contains("123 Nguyen Trai");
        assertThat(persisted.getRestaurantId()).isEqualTo(request.restaurantId());
        assertThat(persisted.getScheduleRequestHash()).isNotBlank();
        assertThat(persisted.getScheduleIdempotencyKey()).isEqualTo(key);
        assertThat(driverRepository.findById(driver.getId()).orElseThrow().isAvailable()).isFalse();
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void duplicateScheduleWithChangedSnapshotConflicts() {
        onlineDriver("Conflict Driver", "0900000099", "59A1-00099");
        UUID orderId = UUID.randomUUID();
        DeliveryRequest original = request(orderId, "12 Le Loi", "123 Nguyen Trai");
        assignmentService.scheduleDelivery(key(orderId), original);

        DeliveryRequest changed = request(orderId, "12 Le Loi", "999 Changed Street");

        assertThatThrownBy(() -> assignmentService.scheduleDelivery(key(orderId), changed))
                .isInstanceOf(DeliveryScheduleConflictException.class);
        assertThat(deliveryRepository.findByOrderId(orderId)).hasValueSatisfying(delivery ->
                assertThat(delivery.getDropoffAddress().text()).contains("123 Nguyen Trai"));
    }

    @Test
    void getByOrderIdReturnsExistingOrNotFound() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> assignmentService.getByOrderId(orderId))
                .isInstanceOf(DeliveryNotFoundException.class);

        assignmentService.scheduleDelivery(key(orderId), request(orderId, "Pickup", "Dropoff"));

        Delivery found = assignmentService.getByOrderId(orderId);
        assertThat(found.getOrderId()).isEqualTo(orderId);
    }

    @Test
    void offlineDriverCannotBeAssigned() {
        Driver driver = new Driver("Offline", "0900000010", VehicleType.MOTORBIKE,
                "59A1-00010", new BigDecimal("4.80"));
        driverRepository.save(driver);
        UUID orderId = UUID.randomUUID();

        DeliveryAssignmentService.AssignmentResult result = assignmentService.scheduleDelivery(
                key(orderId), request(orderId, "Pickup", "Addr"));

        assertThat(result.assigned()).isFalse();
        assertThat(driverRepository.findById(driver.getId()).orElseThrow().isAvailable()).isTrue();
    }

    @Test
    void twoOrdersCannotTakeSameDriver() {
        Driver driver = onlineDriver("Shared", "0900000009", "59A1-00009");
        UUID firstOrder = UUID.randomUUID();
        UUID secondOrder = UUID.randomUUID();

        DeliveryAssignmentService.AssignmentResult first = assignmentService.scheduleDelivery(
                key(firstOrder), request(firstOrder, "P1", "Addr 1"));
        DeliveryAssignmentService.AssignmentResult second = assignmentService.scheduleDelivery(
                key(secondOrder), request(secondOrder, "P2", "Addr 2"));

        assertThat(first.assigned()).isTrue();
        assertThat(first.driverId()).isEqualTo(driver.getId());
        assertThat(second.assigned()).isFalse();
        assertThat(second.deliveryStatus()).isEqualTo(DeliveryStatus.FINDING_DRIVER);
    }

    @Test
    void scheduleDeliveryPersistsPendingAssignmentAndCanRetryWhenDriverOnline() {
        UUID orderId = UUID.randomUUID();
        DeliveryRequest request = request(orderId, "456 Le Loi", "456 Le Loi drop");

        DeliveryAssignmentService.AssignmentResult pending = assignmentService.scheduleDelivery(
                key(orderId), request);

        assertThat(pending.assigned()).isFalse();
        assertThat(deliveryRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(DeliveryStatus.FINDING_DRIVER);

        Driver driver = onlineDriver("Driver Two", "0900000002", "59A1-00002");
        DeliveryAssignmentService.AssignmentResult retried = assignmentService.scheduleDelivery(
                key(orderId), request);

        assertThat(retried.assigned()).isTrue();
        assertThat(retried.deliveryId()).isEqualTo(pending.deliveryId());
        assertThat(retried.driverId()).isEqualTo(driver.getId());
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void scheduleDeliveryPersistsCustomerRestaurantAndAddresses() {
        UUID orderId = UUID.randomUUID();
        DeliveryRequest request = request(orderId, "12 Le Loi", "123 Nguyen Trai");

        assignmentService.scheduleDelivery(key(orderId), request);

        Delivery delivery = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(delivery.getCustomerId()).isEqualTo(request.customerId());
        assertThat(delivery.getRestaurantId()).isEqualTo(request.restaurantId());
        assertThat(delivery.getPickupAddress().text()).isEqualTo("12 Le Loi");
        assertThat(delivery.getDropoffAddress().text()).contains("123 Nguyen Trai");
    }

    private DeliveryRequest request(UUID orderId, String pickupText, String dropoffLine) {
        UUID restaurantId = UUID.randomUUID();
        return new DeliveryRequest(
                orderId,
                UUID.randomUUID(),
                restaurantId,
                new DeliveryRequest.PickupAddressSnapshot(
                        restaurantId, "Restaurant", "0901000000", pickupText, null, null),
                new DeliveryRequest.DropoffAddressSnapshot(
                        dropoffLine, "District 1", "HCM", null, null));
    }

    private static String key(UUID orderId) {
        return "delivery-schedule:" + orderId;
    }

    private Driver onlineDriver(String name, String phone, String plate) {
        Driver driver = new Driver(name, phone, VehicleType.MOTORBIKE, plate, new BigDecimal("4.80"));
        driver.goOnline();
        return driverRepository.save(driver);
    }
}

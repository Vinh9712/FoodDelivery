package com.fooddelivery.delivery.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.Address;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import com.fooddelivery.delivery.domain.model.valueobject.DriverStatus;
import com.fooddelivery.delivery.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import com.fooddelivery.delivery.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for the delivery assignment use case.
 * <p>
 * When a delivery is assigned a driver, this service:
 * 1. Updates the Delivery aggregate with the driver ID.
 * 2. Marks the driver as unavailable.
 * 3. Writes a {@code driver.assigned} outbox event with the full driver snapshot
 *    so that Order Service (and other consumers) receive all necessary data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryAssignmentService {

    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Persist a delivery request and assign an available driver. Repeated calls for
     * the same order return the existing assignment without creating another event.
     */
    @Transactional
    public AssignmentResult scheduleDelivery(UUID orderId, String deliveryAddressSnapshot) {
        Objects.requireNonNull(orderId, "orderId is required");

        Delivery delivery = deliveryRepository.findByOrderIdForUpdate(orderId)
                .orElseGet(() -> createDelivery(orderId, deliveryAddressSnapshot));

        if (delivery.getDriverId() != null) {
            return AssignmentResult.assigned(delivery, "Driver assignment already exists");
        }
        if (delivery.getStatus() == DeliveryStatus.CANCELLED
                || delivery.getStatus() == DeliveryStatus.FAILED) {
            return AssignmentResult.unassigned(delivery, "Delivery is no longer assignable");
        }

        delivery.startFindingDriver();
        List<Driver> candidates = driverRepository.findAssignmentCandidatesForUpdate(
                DriverStatus.ACTIVE, PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            deliveryRepository.save(delivery);
            log.warn("No available driver for order {}. Delivery {} remains FINDING_DRIVER.",
                    orderId, delivery.getId());
            return AssignmentResult.unassigned(delivery, "No available driver");
        }

        Driver driver = candidates.getFirst();
        assignAndRecord(delivery, driver);
        return AssignmentResult.assigned(delivery, "Driver assigned successfully");
    }

    /**
     * Assign a selected driver to a delivery.
     */
    @Transactional
    public void assignDriver(UUID deliveryId, UUID driverId) {
        Delivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found: " + deliveryId));

        if (driverId.equals(delivery.getDriverId())) {
            return;
        }

        Driver driver = driverRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found: " + driverId));
        assignAndRecord(delivery, driver);
    }

    /**
     * Auto-assign an available driver for a newly created delivery.
     */
    @Transactional
    public void autoAssignDriver(UUID orderId) {
        scheduleDelivery(orderId, null);
    }

    private Delivery createDelivery(UUID orderId, String deliveryAddressSnapshot) {
        Address dropoffAddress = deliveryAddressSnapshot == null
                ? null
                : new Address(deliveryAddressSnapshot, null, null);
        Delivery delivery = new Delivery(orderId, null, dropoffAddress, null);
        return deliveryRepository.save(delivery);
    }

    private void assignAndRecord(Delivery delivery, Driver driver) {
        driver.reserveForDelivery();
        delivery.assignDriver(driver.getId());
        deliveryRepository.save(delivery);
        driverRepository.save(driver);

        String payload = buildDriverAssignedPayload(delivery, driver);
        outboxEventRepository.save(new OutboxEvent(
                "Delivery", delivery.getId(), "driver.assigned", payload));

        log.info("Driver {} assigned to delivery {} for order {}",
                driver.getId(), delivery.getId(), delivery.getOrderId());
    }

    private String buildDriverAssignedPayload(Delivery delivery, Driver driver) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("orderId", delivery.getOrderId().toString());
            root.put("deliveryId", delivery.getId().toString());

            ObjectNode driverNode = objectMapper.createObjectNode();
            driverNode.put("driverId", driver.getId().toString());
            driverNode.put("fullName", driver.getFullName());
            driverNode.put("phone", driver.getPhone());
            driverNode.put("vehicleType", driver.getVehicleType().name());
            driverNode.put("licensePlate", driver.getLicensePlate());
            driverNode.put("avgRating", driver.getAvgRating());
            root.set("driver", driverNode);

            root.put("assignedAt", Instant.now().toString());

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize driver.assigned payload", e);
        }
    }

    public record AssignmentResult(
            UUID orderId,
            UUID deliveryId,
            DeliveryStatus deliveryStatus,
            UUID driverId,
            boolean assigned,
            String message
    ) {
        private static AssignmentResult assigned(Delivery delivery, String message) {
            return new AssignmentResult(delivery.getOrderId(), delivery.getId(), delivery.getStatus(),
                    delivery.getDriverId(), true, message);
        }

        private static AssignmentResult unassigned(Delivery delivery, String message) {
            return new AssignmentResult(delivery.getOrderId(), delivery.getId(), delivery.getStatus(),
                    null, false, message);
        }
    }
}

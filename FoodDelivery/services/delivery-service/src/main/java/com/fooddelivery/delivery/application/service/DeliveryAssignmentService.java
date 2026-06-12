package com.fooddelivery.delivery.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import com.fooddelivery.delivery.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
     * Assign the nearest available driver to a delivery.
     */
    @Transactional
    public void assignDriver(UUID deliveryId, UUID driverId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found: " + deliveryId));

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found: " + driverId));

        // 1. Update delivery aggregate
        delivery.assignDriver(driverId);
        deliveryRepository.save(delivery);

        // 2. Mark driver as unavailable
        driver.markUnavailable();
        driverRepository.save(driver);

        // 3. Build outbox event with full driver snapshot payload
        String payload = buildDriverAssignedPayload(delivery, driver);
        OutboxEvent outboxEvent = new OutboxEvent(
                "Delivery",
                delivery.getId(),
                "driver.assigned",
                payload
        );
        outboxEventRepository.save(outboxEvent);

        log.info("Driver {} assigned to delivery {} for order {}",
                driverId, deliveryId, delivery.getOrderId());
    }

    /**
     * Auto-assign the nearest available driver for a newly created delivery.
     */
    @Transactional
    public void autoAssignDriver(UUID orderId) {
        // Create delivery for this order
        Delivery delivery = new Delivery(orderId);
        deliveryRepository.save(delivery);

        // Find an available driver (simplified — in production: nearest by GPS)
        List<Driver> availableDrivers = driverRepository.findByAvailableTrue();
        if (availableDrivers.isEmpty()) {
            log.warn("No available drivers for order {}. Delivery {} remains PENDING.",
                    orderId, delivery.getId());
            return;
        }

        Driver selectedDriver = availableDrivers.getFirst();
        assignDriver(delivery.getId(), selectedDriver.getId());
    }

    private String buildDriverAssignedPayload(Delivery delivery, Driver driver) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("order_id", delivery.getOrderId().toString());
            root.put("delivery_id", delivery.getId().toString());

            ObjectNode driverNode = objectMapper.createObjectNode();
            driverNode.put("driver_id", driver.getId().toString());
            driverNode.put("full_name", driver.getFullName());
            driverNode.put("phone", driver.getPhone());
            driverNode.put("vehicle_type", driver.getVehicleType().name());
            driverNode.put("license_plate", driver.getLicensePlate());
            driverNode.put("avg_rating", driver.getAvgRating());
            root.set("driver", driverNode);

            root.put("assigned_at", Instant.now().toString());

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize driver.assigned payload", e);
        }
    }
}

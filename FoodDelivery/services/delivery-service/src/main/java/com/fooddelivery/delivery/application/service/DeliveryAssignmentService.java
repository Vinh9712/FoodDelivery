package com.fooddelivery.delivery.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.delivery.domain.exception.DeliveryNotFoundException;
import com.fooddelivery.delivery.domain.exception.DriverNotFoundException;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.Address;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import com.fooddelivery.delivery.domain.model.valueobject.DriverStatus;
import com.fooddelivery.delivery.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import com.fooddelivery.delivery.infrastructure.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Delivery assignment use case: auto-assign available online drivers with retry.
 */
@Service
@Slf4j
public class DeliveryAssignmentService {

    private static final List<DeliveryStatus> ACTIVE_STATUSES = List.of(
            DeliveryStatus.DRIVER_ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.DELIVERING);

    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;
    private final int maxAssignmentAttempts;

    public DeliveryAssignmentService(
            DeliveryRepository deliveryRepository,
            DriverRepository driverRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            @Value("${app.delivery.assignment.retry-base-delay:10s}") Duration retryBaseDelay,
            @Value("${app.delivery.assignment.retry-max-delay:10m}") Duration retryMaxDelay,
            @Value("${app.delivery.assignment.max-attempts:10}") int maxAssignmentAttempts) {
        this.deliveryRepository = deliveryRepository;
        this.driverRepository = driverRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.retryBaseDelay = retryBaseDelay;
        this.retryMaxDelay = retryMaxDelay;
        this.maxAssignmentAttempts = maxAssignmentAttempts;
    }

    @Transactional
    public AssignmentResult scheduleDelivery(UUID orderId, String deliveryAddressSnapshot) {
        return scheduleDelivery(orderId, null, deliveryAddressSnapshot);
    }

    @Transactional
    public AssignmentResult scheduleDelivery(UUID orderId, UUID customerId, String deliveryAddressSnapshot) {
        Objects.requireNonNull(orderId, "orderId is required");

        Delivery delivery = deliveryRepository.findByOrderIdForUpdate(orderId)
                .orElseGet(() -> createDelivery(orderId, customerId, deliveryAddressSnapshot));

        if (delivery.getCustomerId() == null && customerId != null) {
            delivery.setCustomerIfMissing(customerId);
            deliveryRepository.save(delivery);
        }

        if (delivery.getDriverId() != null) {
            return AssignmentResult.assigned(delivery, "Driver assignment already exists");
        }
        if (delivery.getStatus() == DeliveryStatus.CANCELLED
                || delivery.getStatus() == DeliveryStatus.FAILED) {
            return AssignmentResult.unassigned(delivery, "Delivery is no longer assignable");
        }

        return tryAssign(delivery);
    }

    /**
     * Retry a single FINDING_DRIVER delivery (own transaction for multi-replica safety).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryAssignment(UUID deliveryId) {
        Delivery delivery = deliveryRepository.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() != DeliveryStatus.FINDING_DRIVER) {
            return;
        }
        Instant now = Instant.now();
        if (delivery.getNextAssignmentAt() != null && delivery.getNextAssignmentAt().isAfter(now)) {
            return;
        }
        tryAssign(delivery);
    }

    @Transactional
    public void assignDriver(UUID deliveryId, UUID driverId) {
        Delivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));

        if (driverId.equals(delivery.getDriverId())) {
            return;
        }

        Driver driver = driverRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new DriverNotFoundException(driverId));
        assignAndRecord(delivery, driver);
    }

    @Transactional
    public void autoAssignDriver(UUID orderId) {
        scheduleDelivery(orderId, null, null);
    }

    /**
     * Release driver availability if they have no other active deliveries.
     */
    @Transactional
    public void releaseDriverIfIdle(UUID driverId, UUID excludingDeliveryId) {
        if (driverId == null) {
            return;
        }
        Driver driver = driverRepository.findByIdForUpdate(driverId).orElse(null);
        if (driver == null) {
            return;
        }
        long active = deliveryRepository.countActiveByDriver(driverId, ACTIVE_STATUSES, excludingDeliveryId);
        if (active == 0) {
            driver.markAvailable();
            driverRepository.save(driver);
            log.info("Released driver {} (no remaining active deliveries)", driverId);
        }
    }

    private AssignmentResult tryAssign(Delivery delivery) {
        delivery.startFindingDriver();

        List<Driver> candidates = driverRepository.findAssignmentCandidatesForUpdate(
                DriverStatus.ACTIVE, PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            Instant now = Instant.now();
            int nextAttempt = delivery.getAssignmentAttempts() + 1;
            if (nextAttempt >= maxAssignmentAttempts) {
                delivery.fail("No available driver after " + nextAttempt + " assignment attempts");
                deliveryRepository.save(delivery);
                outboxEventRepository.save(new OutboxEvent(
                        "Delivery", delivery.getId(), "delivery.failed",
                        buildFailurePayload(delivery, delivery.getFailureReason())));
                log.error("Delivery {} moved to FAILED after max assignment attempts", delivery.getId());
                return AssignmentResult.unassigned(delivery, delivery.getFailureReason());
            }
            Instant retryAt = now.plus(backoffFor(nextAttempt));
            delivery.recordAssignmentFailure("No available online driver", retryAt);
            deliveryRepository.save(delivery);
            log.warn("No available driver for order {}. Delivery {} retry at {}",
                    delivery.getOrderId(), delivery.getId(), retryAt);
            return AssignmentResult.unassigned(delivery, "No available driver");
        }

        Driver driver = candidates.getFirst();
        assignAndRecord(delivery, driver);
        return AssignmentResult.assigned(delivery, "Driver assigned successfully");
    }

    private Delivery createDelivery(UUID orderId, UUID customerId, String deliveryAddressSnapshot) {
        Address dropoffAddress = deliveryAddressSnapshot == null
                ? null
                : new Address(deliveryAddressSnapshot, null, null);
        Delivery delivery = new Delivery(orderId, customerId, null, dropoffAddress, null);
        return deliveryRepository.save(delivery);
    }

    private void assignAndRecord(Delivery delivery, Driver driver) {
        driver.reserveForDelivery();
        delivery.assignDriver(driver.getId());
        deliveryRepository.save(delivery);
        driverRepository.save(driver);

        outboxEventRepository.save(new OutboxEvent(
                "Delivery", delivery.getId(), "driver.assigned",
                buildDriverAssignedPayload(delivery, driver)));

        log.info("Driver {} assigned to delivery {} for order {}",
                driver.getId(), delivery.getId(), delivery.getOrderId());
    }

    private Duration backoffFor(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        long delayMillis;
        try {
            delayMillis = Math.multiplyExact(retryBaseDelay.toMillis(), multiplier);
        } catch (ArithmeticException ex) {
            delayMillis = retryMaxDelay.toMillis();
        }
        return Duration.ofMillis(Math.min(delayMillis, retryMaxDelay.toMillis()));
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
            if (driver.getAvgRating() != null) {
                driverNode.put("avgRating", driver.getAvgRating());
            }
            root.set("driver", driverNode);
            root.put("assignedAt", Instant.now().toString());
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize driver.assigned payload", e);
        }
    }

    private String buildFailurePayload(Delivery delivery, String reason) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("orderId", delivery.getOrderId().toString());
            root.put("deliveryId", delivery.getId().toString());
            root.put("status", delivery.getStatus().name());
            root.put("reason", reason == null ? "Assignment failed" : reason);
            root.put("failedAt", Instant.now().toString());
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize delivery.failed payload", e);
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

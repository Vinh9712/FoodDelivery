package com.fooddelivery.delivery.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.delivery.domain.exception.DeliveryAccessDeniedException;
import com.fooddelivery.delivery.domain.exception.DeliveryNotFoundException;
import com.fooddelivery.delivery.domain.exception.DriverNotFoundException;
import com.fooddelivery.delivery.domain.exception.InvalidDeliveryStateException;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.DeliveryTracking;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import com.fooddelivery.delivery.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import com.fooddelivery.delivery.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryLifecycleService {

    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DeliveryAssignmentService assignmentService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Delivery accept(UUID deliveryId, UUID driverUserId) {
        Driver driver = requireDriverByUser(driverUserId);
        Delivery delivery = lockDelivery(deliveryId);

        if (driver.getId().equals(delivery.getDriverId())
                && delivery.getStatus() == DeliveryStatus.DRIVER_ASSIGNED) {
            return delivery;
        }
        if (delivery.getDriverId() != null) {
            throw new DeliveryAccessDeniedException("Delivery is assigned to another driver");
        }
        if (delivery.getStatus() != DeliveryStatus.PENDING
                && delivery.getStatus() != DeliveryStatus.FINDING_DRIVER) {
            throw new InvalidDeliveryStateException(delivery.getStatus());
        }

        driver.reserveForDelivery();
        delivery.assignDriver(driver.getId());
        driverRepository.save(driver);
        deliveryRepository.save(delivery);
        outboxEventRepository.save(new OutboxEvent(
                "Delivery", delivery.getId(), "driver.assigned",
                assignmentService.buildDriverAssignedPayload(delivery, driver)));
        return delivery;
    }

    @Transactional
    public Delivery pickUp(UUID deliveryId, UUID driverUserId) {
        Driver driver = requireDriverByUser(driverUserId);
        Delivery delivery = lockDelivery(deliveryId);
        assertAssignedDriver(delivery, driver.getId());
        delivery.pickUp();
        deliveryRepository.save(delivery);
        outboxEventRepository.save(new OutboxEvent(
                "Delivery", delivery.getId(), "delivery.picked-up",
                lifecyclePayload(delivery, "delivery.picked-up")));
        return delivery;
    }

    @Transactional
    public Delivery startDelivery(UUID deliveryId, UUID driverUserId) {
        Driver driver = requireDriverByUser(driverUserId);
        Delivery delivery = lockDelivery(deliveryId);
        assertAssignedDriver(delivery, driver.getId());
        delivery.startDelivering();
        deliveryRepository.save(delivery);
        outboxEventRepository.save(new OutboxEvent(
                "Delivery", delivery.getId(), "delivery.in-transit",
                lifecyclePayload(delivery, "delivery.in-transit")));
        return delivery;
    }

    @Transactional
    public Delivery complete(UUID deliveryId, UUID driverUserId) {
        Driver driver = requireDriverByUser(driverUserId);
        Delivery delivery = lockDelivery(deliveryId);
        assertAssignedDriver(delivery, driver.getId());
        UUID assignedDriverId = delivery.getDriverId();
        delivery.complete();
        deliveryRepository.save(delivery);
        outboxEventRepository.save(new OutboxEvent(
                "Delivery", delivery.getId(), "delivery.completed",
                lifecyclePayload(delivery, "delivery.completed")));
        assignmentService.releaseDriverIfIdle(assignedDriverId, delivery.getId());
        return delivery;
    }

    @Transactional
    public Delivery fail(UUID deliveryId, UUID driverUserId, String reason) {
        Driver driver = requireDriverByUser(driverUserId);
        Delivery delivery = lockDelivery(deliveryId);
        assertAssignedDriver(delivery, driver.getId());
        UUID assignedDriverId = delivery.getDriverId();
        delivery.fail(reason == null ? "Delivery failed" : reason);
        deliveryRepository.save(delivery);
        outboxEventRepository.save(new OutboxEvent(
                "Delivery", delivery.getId(), "delivery.failed",
                lifecyclePayload(delivery, "delivery.failed")));
        assignmentService.releaseDriverIfIdle(assignedDriverId, delivery.getId());
        return delivery;
    }

    @Transactional(readOnly = true)
    public Delivery getDelivery(UUID deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
    }

    @Transactional(readOnly = true)
    public Delivery getDeliveryByOrderId(UUID orderId) {
        return deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DeliveryNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public List<DeliveryTracking> getTracking(UUID deliveryId) {
        Delivery delivery = getDelivery(deliveryId);
        return List.copyOf(delivery.getTrackingPoints());
    }

    @Transactional
    public Delivery addTracking(UUID deliveryId, UUID driverUserId, BigDecimal lat, BigDecimal lng) {
        Driver driver = requireDriverByUser(driverUserId);
        Delivery delivery = lockDelivery(deliveryId);
        assertAssignedDriver(delivery, driver.getId());
        delivery.addTrackingPoint(lat, lng);
        return deliveryRepository.save(delivery);
    }

    private Delivery lockDelivery(UUID deliveryId) {
        return deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
    }

    private Driver requireDriverByUser(UUID userId) {
        return driverRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> DriverNotFoundException.forUser(userId));
    }

    private void assertAssignedDriver(Delivery delivery, UUID driverId) {
        if (!driverId.equals(delivery.getDriverId())) {
            throw new DeliveryAccessDeniedException("Driver is not assigned to this delivery");
        }
    }

    private String lifecyclePayload(Delivery delivery, String eventType) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("orderId", delivery.getOrderId().toString());
            root.put("deliveryId", delivery.getId().toString());
            root.put("status", delivery.getStatus().name());
            root.put("eventType", eventType);
            if (delivery.getDriverId() != null) {
                root.put("driverId", delivery.getDriverId().toString());
            }
            if (delivery.getFailureReason() != null) {
                root.put("reason", delivery.getFailureReason());
            }
            root.put("occurredAt", Instant.now().toString());
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize " + eventType + " payload", e);
        }
    }
}

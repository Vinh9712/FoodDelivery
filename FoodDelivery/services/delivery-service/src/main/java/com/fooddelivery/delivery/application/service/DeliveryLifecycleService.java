package com.fooddelivery.delivery.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.delivery.DeliveryEventPayloads;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class DeliveryLifecycleService {

    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DeliveryAssignmentService assignmentService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DeliveryLifecycleService(
            DeliveryRepository deliveryRepository,
            DriverRepository driverRepository,
            OutboxEventRepository outboxEventRepository,
            DeliveryAssignmentService assignmentService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.driverRepository = driverRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.assignmentService = assignmentService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

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

        Instant assignedAt = clock.instant();
        driver.reserveForDelivery();
        delivery.assignDriver(driver.getId(), assignedAt);
        driverRepository.save(driver);
        saveOutbox(delivery, EventContracts.DRIVER_ASSIGNED,
                assignmentService.buildDriverAssignedPayload(delivery, driver, assignedAt), assignedAt);
        return delivery;
    }

    @Transactional
    public Delivery pickUp(UUID deliveryId, UUID driverUserId) {
        Driver driver = requireDriverByUser(driverUserId);
        Delivery delivery = lockDelivery(deliveryId);
        assertAssignedDriver(delivery, driver.getId());
        Instant pickedUpAt = clock.instant();
        delivery.pickUp(pickedUpAt);
        saveOutbox(delivery, EventContracts.DELIVERY_PICKED_UP,
                lifecyclePayload(delivery, EventContracts.DELIVERY_PICKED_UP, pickedUpAt), pickedUpAt);
        return delivery;
    }

    @Transactional
    public Delivery startDelivery(UUID deliveryId, UUID driverUserId) {
        Driver driver = requireDriverByUser(driverUserId);
        Delivery delivery = lockDelivery(deliveryId);
        assertAssignedDriver(delivery, driver.getId());
        Instant startedAt = clock.instant();
        delivery.startDelivering(startedAt);
        saveOutbox(delivery, EventContracts.DELIVERY_IN_TRANSIT,
                lifecyclePayload(delivery, EventContracts.DELIVERY_IN_TRANSIT, startedAt), startedAt);
        return delivery;
    }

    @Transactional
    public Delivery complete(UUID deliveryId, UUID driverUserId) {
        Driver driver = requireDriverByUser(driverUserId);
        Delivery delivery = lockDelivery(deliveryId);
        assertAssignedDriver(delivery, driver.getId());
        UUID assignedDriverId = delivery.getDriverId();
        Instant deliveredAt = clock.instant();
        delivery.complete(deliveredAt);
        saveOutbox(delivery, EventContracts.DELIVERY_COMPLETED,
                lifecyclePayload(delivery, EventContracts.DELIVERY_COMPLETED, deliveredAt), deliveredAt);
        assignmentService.releaseDriverIfIdle(assignedDriverId, delivery.getId());
        return delivery;
    }

    @Transactional
    public Delivery fail(UUID deliveryId, UUID driverUserId, String reason) {
        Driver driver = requireDriverByUser(driverUserId);
        Delivery delivery = lockDelivery(deliveryId);
        assertAssignedDriver(delivery, driver.getId());
        UUID assignedDriverId = delivery.getDriverId();
        Instant failedAt = clock.instant();
        delivery.fail(reason == null ? "Delivery failed" : reason, failedAt);
        saveOutbox(delivery, EventContracts.DELIVERY_FAILED,
                lifecyclePayload(delivery, EventContracts.DELIVERY_FAILED, failedAt), failedAt);
        assignmentService.releaseDriverIfIdle(assignedDriverId, delivery.getId());
        return delivery;
    }

    /**
     * Cancel delivery when order is confirmed cancelled. Pre-pickup only; does not emit DeliveryFailed.
     * No delivery row → no-op success (e.g. cancelled before schedule).
     */
    @Transactional
    public Delivery.CancelFromOrderResult cancelFromOrder(UUID orderId, String reason) {
        Delivery delivery = deliveryRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (delivery == null) {
            log.info("OrderCancelled for order {} with no delivery — advancing as no-op", orderId);
            return Delivery.CancelFromOrderResult.alreadyCancelled();
        }
        Instant at = clock.instant();
        Delivery.CancelFromOrderResult result = delivery.cancelFromOrder(reason, at);
        if (result.mutated()) {
            deliveryRepository.save(delivery);
            if (result.releaseDriver()) {
                assignmentService.releaseDriverIfIdle(result.driverId(), delivery.getId());
            }
            log.info("Delivery {} cancelled from OrderCancelled (order {})", delivery.getId(), orderId);
        } else if (result.afterPickup()) {
            log.warn(
                    "OrderCancelled for order {} but delivery {} already {} — leaving delivery unchanged",
                    orderId, delivery.getId(), delivery.getStatus());
        }
        return result;
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

    private void saveOutbox(Delivery delivery, String eventType, String payload, Instant occurredAt) {
        long sequence = delivery.nextEventSequence();
        deliveryRepository.save(delivery);
        outboxEventRepository.save(new OutboxEvent(
                "Delivery",
                delivery.getId(),
                eventType,
                1,
                sequence,
                delivery.getOrderId().toString(),
                payload,
                occurredAt));
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

    private String lifecyclePayload(Delivery delivery, String eventType, Instant occurredAt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("orderId", delivery.getOrderId().toString());
            root.put("deliveryId", delivery.getId().toString());
            if (delivery.getCustomerId() != null) {
                root.put("customerId", delivery.getCustomerId().toString());
            }
            if (delivery.getDriverId() != null) {
                root.put("driverId", delivery.getDriverId().toString());
            }
            switch (eventType) {
                case EventContracts.DELIVERY_PICKED_UP ->
                        root.put("pickedUpAt", occurredAt.toString());
                case EventContracts.DELIVERY_IN_TRANSIT ->
                        root.put("deliveryStartedAt", occurredAt.toString());
                case EventContracts.DELIVERY_COMPLETED ->
                        root.put("deliveredAt", occurredAt.toString());
                case EventContracts.DELIVERY_FAILED -> {
                    root.put("failureCode", DeliveryEventPayloads.FailureCode.DRIVER_REPORTED.name());
                    root.put("reason", delivery.getFailureReason() == null
                            ? "Delivery failed" : delivery.getFailureReason());
                    root.put("failedAt", occurredAt.toString());
                }
                default -> root.put("occurredAt", occurredAt.toString());
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize " + eventType + " payload", e);
        }
    }
}

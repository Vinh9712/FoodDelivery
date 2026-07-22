package com.fooddelivery.delivery.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.delivery.DeliveryEventPayloads;
import com.fooddelivery.delivery.api.dto.DeliveryRequest;
import com.fooddelivery.delivery.domain.exception.DeliveryNotFoundException;
import com.fooddelivery.delivery.domain.exception.DeliveryScheduleConflictException;
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
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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
    private final Clock clock;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;
    private final int maxAssignmentAttempts;

    public DeliveryAssignmentService(
            DeliveryRepository deliveryRepository,
            DriverRepository driverRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.delivery.assignment.retry-base-delay:10s}") Duration retryBaseDelay,
            @Value("${app.delivery.assignment.retry-max-delay:10m}") Duration retryMaxDelay,
            @Value("${app.delivery.assignment.max-attempts:10}") int maxAssignmentAttempts) {
        this.deliveryRepository = deliveryRepository;
        this.driverRepository = driverRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.retryBaseDelay = retryBaseDelay;
        this.retryMaxDelay = retryMaxDelay;
        this.maxAssignmentAttempts = maxAssignmentAttempts;
    }

    @Transactional
    public AssignmentResult scheduleDelivery(String idempotencyKey, DeliveryRequest request) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(request.orderId(), "orderId is required");
        validateIdempotencyKey(idempotencyKey, request.orderId());
        requireRequestShape(request);

        String requestHash = computeScheduleRequestHash(request);

        Delivery delivery = deliveryRepository.findByOrderIdForUpdate(request.orderId())
                .orElseGet(() -> createDelivery(request, requestHash, idempotencyKey));

        if (delivery.getScheduleRequestHash() != null
                && !delivery.getScheduleRequestHash().equals(requestHash)) {
            throw new DeliveryScheduleConflictException(request.orderId());
        }
        delivery.bindScheduleIdentityIfMissing(requestHash, idempotencyKey);
        delivery.setCustomerIfMissing(request.customerId());
        delivery.setRestaurantIfMissing(request.restaurantId());
        if (delivery.getPickupAddress() == null && request.pickupAddressSnapshot() != null) {
            delivery.setPickupAddress(toPickupAddress(request.pickupAddressSnapshot()));
        }
        if (delivery.getDropoffAddress() == null && request.dropoffAddressSnapshot() != null) {
            delivery.setDropoffAddress(toDropoffAddress(request.dropoffAddressSnapshot()));
        }
        deliveryRepository.save(delivery);

        if (delivery.getDriverId() != null) {
            return AssignmentResult.assigned(delivery, "Driver assignment already exists");
        }
        if (delivery.getStatus() == DeliveryStatus.CANCELLED
                || delivery.getStatus() == DeliveryStatus.FAILED) {
            return AssignmentResult.unassigned(delivery, "Delivery is no longer assignable");
        }

        return tryAssign(delivery);
    }

    @Transactional(readOnly = true)
    public Delivery getByOrderId(UUID orderId) {
        return deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DeliveryNotFoundException(orderId));
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
        Instant now = clock.instant();
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
            Instant now = clock.instant();
            int nextAttempt = delivery.getAssignmentAttempts() + 1;
            if (nextAttempt >= maxAssignmentAttempts) {
                Instant failedAt = clock.instant();
                delivery.fail("No available driver after " + nextAttempt + " assignment attempts", failedAt);
                deliveryRepository.save(delivery);
                saveOutbox(delivery, EventContracts.DELIVERY_FAILED,
                        buildFailurePayload(delivery, delivery.getFailureReason(),
                                DeliveryEventPayloads.FailureCode.NO_DRIVER, failedAt),
                        failedAt);
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

    private Delivery createDelivery(DeliveryRequest request, String requestHash, String idempotencyKey) {
        Delivery delivery = new Delivery(
                request.orderId(),
                request.customerId(),
                request.restaurantId(),
                toPickupAddress(request.pickupAddressSnapshot()),
                toDropoffAddress(request.dropoffAddressSnapshot()),
                null,
                requestHash,
                idempotencyKey);
        return deliveryRepository.save(delivery);
    }

    private void assignAndRecord(Delivery delivery, Driver driver) {
        Instant assignedAt = clock.instant();
        driver.reserveForDelivery();
        delivery.assignDriver(driver.getId(), assignedAt);
        deliveryRepository.save(delivery);
        driverRepository.save(driver);

        saveOutbox(delivery, EventContracts.DRIVER_ASSIGNED,
                buildDriverAssignedPayload(delivery, driver, assignedAt), assignedAt);

        log.info("Driver {} assigned to delivery {} for order {}",
                driver.getId(), delivery.getId(), delivery.getOrderId());
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

    String buildDriverAssignedPayload(Delivery delivery, Driver driver) {
        return buildDriverAssignedPayload(delivery, driver, clock.instant());
    }

    String buildDriverAssignedPayload(Delivery delivery, Driver driver, Instant assignedAt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("orderId", delivery.getOrderId().toString());
            root.put("deliveryId", delivery.getId().toString());
            if (delivery.getCustomerId() != null) {
                root.put("customerId", delivery.getCustomerId().toString());
            }

            ObjectNode driverNode = objectMapper.createObjectNode();
            driverNode.put("driverId", driver.getId().toString());
            driverNode.put("fullName", driver.getFullName());
            driverNode.put("phone", driver.getPhone());
            driverNode.put("vehicleType", driver.getVehicleType().name());
            driverNode.put("licensePlate", driver.getLicensePlate());
            root.set("driver", driverNode);
            root.put("assignedAt", assignedAt.toString());
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize DriverAssigned payload", e);
        }
    }

    private String buildFailurePayload(
            Delivery delivery,
            String reason,
            DeliveryEventPayloads.FailureCode failureCode,
            Instant failedAt) {
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
            root.put("failureCode", failureCode.name());
            root.put("reason", reason == null ? "Assignment failed" : reason);
            root.put("failedAt", failedAt.toString());
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize DeliveryFailed payload", e);
        }
    }

    static String computeScheduleRequestHash(DeliveryRequest request) {
        DeliveryRequest.PickupAddressSnapshot pickup = request.pickupAddressSnapshot();
        DeliveryRequest.DropoffAddressSnapshot dropoff = request.dropoffAddressSnapshot();
        String canonical = String.join("|",
                nullToEmpty(request.orderId()),
                nullToEmpty(request.customerId()),
                nullToEmpty(request.restaurantId()),
                nullToEmpty(pickup == null ? null : pickup.restaurantId()),
                nullToEmpty(pickup == null ? null : pickup.name()),
                nullToEmpty(pickup == null ? null : pickup.phone()),
                nullToEmpty(pickup == null ? null : pickup.addressText()),
                decimal(pickup == null ? null : pickup.latitude()),
                decimal(pickup == null ? null : pickup.longitude()),
                nullToEmpty(dropoff == null ? null : dropoff.addressLine()),
                nullToEmpty(dropoff == null ? null : dropoff.district()),
                nullToEmpty(dropoff == null ? null : dropoff.city()),
                decimal(dropoff == null ? null : dropoff.latitude()),
                decimal(dropoff == null ? null : dropoff.longitude()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void validateIdempotencyKey(String idempotencyKey, UUID orderId) {
        String expected = "delivery-schedule:" + orderId;
        if (!StringUtils.hasText(idempotencyKey) || !expected.equals(idempotencyKey)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must equal delivery-schedule:{orderId}");
        }
    }

    private static void requireRequestShape(DeliveryRequest request) {
        if (request.customerId() == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (request.restaurantId() == null) {
            throw new IllegalArgumentException("restaurantId is required");
        }
        if (request.pickupAddressSnapshot() == null
                || !StringUtils.hasText(request.pickupAddressSnapshot().addressText())) {
            throw new IllegalArgumentException("pickupAddressSnapshot is required");
        }
        if (request.dropoffAddressSnapshot() == null
                || !StringUtils.hasText(request.dropoffAddressSnapshot().addressLine())) {
            throw new IllegalArgumentException("dropoffAddressSnapshot is required");
        }
    }

    private static Address toPickupAddress(DeliveryRequest.PickupAddressSnapshot pickup) {
        if (pickup == null || !StringUtils.hasText(pickup.addressText())) {
            return null;
        }
        return new Address(pickup.addressText(), pickup.latitude(), pickup.longitude());
    }

    private static Address toDropoffAddress(DeliveryRequest.DropoffAddressSnapshot dropoff) {
        if (dropoff == null || !StringUtils.hasText(dropoff.addressLine())) {
            return null;
        }
        String text = dropoff.addressLine();
        if (StringUtils.hasText(dropoff.district()) || StringUtils.hasText(dropoff.city())) {
            text = String.join(", ",
                    dropoff.addressLine(),
                    nullToEmpty(dropoff.district()),
                    nullToEmpty(dropoff.city())).replaceAll(",\\s*,", ",").replaceAll(",\\s*$", "");
        }
        return new Address(text, dropoff.latitude(), dropoff.longitude());
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
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

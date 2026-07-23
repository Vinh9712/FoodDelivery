package com.fooddelivery.delivery.domain.model;

import com.fooddelivery.delivery.domain.exception.InvalidDeliveryStateException;
import com.fooddelivery.delivery.domain.model.valueobject.Address;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.fooddelivery.delivery.domain.util.UuidCreator;

/**
 * Delivery aggregate root — tracks the lifecycle of a delivery for an order.
 */
@Entity
@Table(name = "deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(name = "pickup_address")
    private String pickupAddressText;

    @Column(name = "pickup_latitude", precision = 9, scale = 6)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", precision = 9, scale = 6)
    private BigDecimal pickupLongitude;

    @Column(name = "dropoff_address", columnDefinition = "text")
    private String dropoffAddressText;

    @Column(name = "dropoff_latitude", precision = 9, scale = 6)
    private BigDecimal dropoffLatitude;

    @Column(name = "dropoff_longitude", precision = 9, scale = 6)
    private BigDecimal dropoffLongitude;

    @Column(name = "schedule_request_hash", length = 64)
    private String scheduleRequestHash;

    @Column(name = "schedule_idempotency_key", length = 200)
    private String scheduleIdempotencyKey;

    @Column(name = "event_sequence", nullable = false)
    private long eventSequence;

    @Column(name = "estimated_arrival_at")
    private Instant estimatedArrivalAt;

    @Column(name = "driver_assigned_at")
    private Instant driverAssignedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "distance_km", precision = 5, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "assignment_attempts", nullable = false)
    private int assignmentAttempts;

    @Column(name = "next_assignment_at")
    private Instant nextAssignmentAt;

    @Column(name = "last_assignment_error", length = 1000)
    private String lastAssignmentError;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private List<DeliveryTracking> trackingPoints = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Delivery(UUID orderId) {
        this(orderId, null, null, null, null, null, null, null);
    }

    public Delivery(UUID orderId, Address pickupAddress, Address dropoffAddress, BigDecimal distanceKm) {
        this(orderId, null, null, pickupAddress, dropoffAddress, distanceKm, null, null);
    }

    public Delivery(UUID orderId, UUID customerId, Address pickupAddress, Address dropoffAddress, BigDecimal distanceKm) {
        this(orderId, customerId, null, pickupAddress, dropoffAddress, distanceKm, null, null);
    }

    public Delivery(
            UUID orderId,
            UUID customerId,
            UUID restaurantId,
            Address pickupAddress,
            Address dropoffAddress,
            BigDecimal distanceKm,
            String scheduleRequestHash,
            String scheduleIdempotencyKey) {
        this.id = UuidCreator.nextUuidV7();
        this.orderId = orderId;
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.status = DeliveryStatus.PENDING;
        setPickupAddress(pickupAddress);
        setDropoffAddress(dropoffAddress);
        this.distanceKm = distanceKm;
        this.scheduleRequestHash = scheduleRequestHash;
        this.scheduleIdempotencyKey = scheduleIdempotencyKey;
        this.eventSequence = 0;
        this.assignmentAttempts = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Address getPickupAddress() {
        if (this.pickupAddressText == null) return null;
        return new Address(this.pickupAddressText, this.pickupLatitude, this.pickupLongitude);
    }

    public Address getDropoffAddress() {
        if (this.dropoffAddressText == null) return null;
        return new Address(this.dropoffAddressText, this.dropoffLatitude, this.dropoffLongitude);
    }

    public void setPickupAddress(Address address) {
        if (address != null) {
            this.pickupAddressText = address.text();
            this.pickupLatitude = address.latitude();
            this.pickupLongitude = address.longitude();
        }
    }

    public void setDropoffAddress(Address address) {
        if (address != null) {
            this.dropoffAddressText = address.text();
            this.dropoffLatitude = address.latitude();
            this.dropoffLongitude = address.longitude();
        }
    }

    public void assignDriver(UUID driverId) {
        assignDriver(driverId, Instant.now());
    }

    public void assignDriver(UUID driverId, Instant assignedAt) {
        if (status != DeliveryStatus.PENDING && status != DeliveryStatus.FINDING_DRIVER) {
            throw new InvalidDeliveryStateException(status);
        }
        Instant at = assignedAt != null ? assignedAt : Instant.now();
        this.driverId = driverId;
        this.status = DeliveryStatus.DRIVER_ASSIGNED;
        this.driverAssignedAt = at;
        this.nextAssignmentAt = null;
        this.lastAssignmentError = null;
        this.updatedAt = at;
    }

    public void startFindingDriver() {
        if (status != DeliveryStatus.PENDING && status != DeliveryStatus.FINDING_DRIVER) {
            throw new InvalidDeliveryStateException(status);
        }
        this.status = DeliveryStatus.FINDING_DRIVER;
        if (this.nextAssignmentAt == null) {
            this.nextAssignmentAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Driver rejects assignment: clear driver and re-queue FINDING_DRIVER for reassignment.
     *
     * @return previous driver id (may be null)
     */
    public UUID rejectAssignment(Instant now) {
        if (status != DeliveryStatus.DRIVER_ASSIGNED) {
            throw new InvalidDeliveryStateException(status);
        }
        UUID previous = this.driverId;
        this.driverId = null;
        this.driverAssignedAt = null;
        this.status = DeliveryStatus.FINDING_DRIVER;
        this.nextAssignmentAt = now != null ? now : Instant.now();
        this.updatedAt = this.nextAssignmentAt;
        return previous;
    }

    public void setCustomerIfMissing(UUID customerId) {
        if (this.customerId == null && customerId != null) {
            this.customerId = customerId;
            this.updatedAt = Instant.now();
        }
    }

    public void setRestaurantIfMissing(UUID restaurantId) {
        if (this.restaurantId == null && restaurantId != null) {
            this.restaurantId = restaurantId;
            this.updatedAt = Instant.now();
        }
    }

    public void bindScheduleIdentityIfMissing(String scheduleRequestHash, String scheduleIdempotencyKey) {
        if (this.scheduleRequestHash == null && scheduleRequestHash != null) {
            this.scheduleRequestHash = scheduleRequestHash;
        }
        if (this.scheduleIdempotencyKey == null && scheduleIdempotencyKey != null) {
            this.scheduleIdempotencyKey = scheduleIdempotencyKey;
        }
        this.updatedAt = Instant.now();
    }

    public long nextEventSequence() {
        this.eventSequence++;
        this.updatedAt = Instant.now();
        return this.eventSequence;
    }

    public void recordAssignmentFailure(String error, Instant retryAt) {
        this.assignmentAttempts++;
        this.lastAssignmentError = truncate(error, 1000);
        this.nextAssignmentAt = retryAt;
        this.updatedAt = Instant.now();
    }

    public void acceptByDriver(UUID driverId) {
        if (status == DeliveryStatus.DRIVER_ASSIGNED) {
            if (!driverId.equals(this.driverId)) {
                throw new InvalidDeliveryStateException(status);
            }
            this.updatedAt = Instant.now();
            return;
        }
        if (status != DeliveryStatus.PENDING && status != DeliveryStatus.FINDING_DRIVER) {
            throw new InvalidDeliveryStateException(status);
        }
        assignDriver(driverId);
    }

    public void pickUp() {
        pickUp(Instant.now());
    }

    public void pickUp(Instant pickedUpAt) {
        requireStatus(DeliveryStatus.DRIVER_ASSIGNED);
        Instant at = pickedUpAt != null ? pickedUpAt : Instant.now();
        this.status = DeliveryStatus.PICKED_UP;
        this.pickedUpAt = at;
        this.updatedAt = at;
    }

    public void startDelivering() {
        startDelivering(Instant.now());
    }

    public void startDelivering(Instant deliveryStartedAt) {
        requireStatus(DeliveryStatus.PICKED_UP);
        Instant at = deliveryStartedAt != null ? deliveryStartedAt : Instant.now();
        this.status = DeliveryStatus.DELIVERING;
        this.updatedAt = at;
    }

    public void complete() {
        complete(Instant.now());
    }

    public void complete(Instant deliveredAt) {
        requireStatus(DeliveryStatus.DELIVERING);
        Instant at = deliveredAt != null ? deliveredAt : Instant.now();
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = at;
        this.updatedAt = at;
    }

    public void fail(String reason) {
        fail(reason, Instant.now());
    }

    public void fail(String reason, Instant failedAt) {
        if (status == DeliveryStatus.DELIVERED || status == DeliveryStatus.CANCELLED
                || status == DeliveryStatus.FAILED) {
            throw new InvalidDeliveryStateException(status);
        }
        Instant at = failedAt != null ? failedAt : Instant.now();
        this.status = DeliveryStatus.FAILED;
        this.failureReason = truncate(reason, 1000);
        this.nextAssignmentAt = null;
        this.updatedAt = at;
    }

    public void cancel(String reason) {
        cancel(reason, Instant.now());
    }

    public void cancel(String reason, Instant cancelledAt) {
        CancelFromOrderResult result = cancelFromOrder(reason, cancelledAt);
        if (result.afterPickup() || result.terminalNonCancel()) {
            throw new InvalidDeliveryStateException(status);
        }
    }

    /**
     * Cancel from confirmed {@code OrderCancelled}. Idempotent for already-cancelled.
     * Valid pre-pickup; post-pickup leaves state unchanged and signals alert.
     */
    public CancelFromOrderResult cancelFromOrder(String reason, Instant cancelledAt) {
        Instant at = cancelledAt != null ? cancelledAt : Instant.now();
        if (status == DeliveryStatus.CANCELLED) {
            return CancelFromOrderResult.alreadyCancelled();
        }
        if (status == DeliveryStatus.DELIVERED || status == DeliveryStatus.FAILED) {
            return CancelFromOrderResult.terminalNonCancel(status);
        }
        if (status == DeliveryStatus.PICKED_UP || status == DeliveryStatus.DELIVERING) {
            return CancelFromOrderResult.afterPickup(status);
        }
        // PENDING, FINDING_DRIVER, DRIVER_ASSIGNED
        UUID reservedDriver = this.driverId;
        this.status = DeliveryStatus.CANCELLED;
        this.failureReason = truncate(reason == null ? "Order cancelled" : reason, 1000);
        this.nextAssignmentAt = null;
        this.updatedAt = at;
        boolean release = reservedDriver != null;
        return CancelFromOrderResult.cancelled(release, reservedDriver);
    }

    /**
     * Result of {@link #cancelFromOrder(String, Instant)}.
     */
    public record CancelFromOrderResult(
            boolean mutated,
            boolean releaseDriver,
            UUID driverId,
            boolean afterPickup,
            boolean terminalNonCancel,
            DeliveryStatus observedStatus) {

        public static CancelFromOrderResult alreadyCancelled() {
            return new CancelFromOrderResult(false, false, null, false, false, DeliveryStatus.CANCELLED);
        }

        public static CancelFromOrderResult cancelled(boolean releaseDriver, UUID driverId) {
            return new CancelFromOrderResult(true, releaseDriver, driverId, false, false, DeliveryStatus.CANCELLED);
        }

        public static CancelFromOrderResult afterPickup(DeliveryStatus status) {
            return new CancelFromOrderResult(false, false, null, true, false, status);
        }

        public static CancelFromOrderResult terminalNonCancel(DeliveryStatus status) {
            return new CancelFromOrderResult(false, false, null, false, true, status);
        }
    }

    public boolean isActiveAssignment() {
        return status == DeliveryStatus.DRIVER_ASSIGNED
                || status == DeliveryStatus.PICKED_UP
                || status == DeliveryStatus.DELIVERING;
    }

    public boolean isTerminal() {
        return status == DeliveryStatus.DELIVERED
                || status == DeliveryStatus.FAILED
                || status == DeliveryStatus.CANCELLED;
    }

    public void addTrackingPoint(BigDecimal lat, BigDecimal lng) {
        if (status != DeliveryStatus.DRIVER_ASSIGNED
                && status != DeliveryStatus.PICKED_UP
                && status != DeliveryStatus.DELIVERING) {
            throw new InvalidDeliveryStateException(status);
        }
        trackingPoints.add(DeliveryTracking.of(this.id, lat, lng, this.status));
        this.updatedAt = Instant.now();
    }

    private void requireStatus(DeliveryStatus expected) {
        if (this.status != expected) {
            throw new InvalidDeliveryStateException(this.status);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

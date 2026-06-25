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

    @Column(name = "dropoff_address")
    private String dropoffAddressText;

    @Column(name = "dropoff_latitude", precision = 9, scale = 6)
    private BigDecimal dropoffLatitude;

    @Column(name = "dropoff_longitude", precision = 9, scale = 6)
    private BigDecimal dropoffLongitude;

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

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private List<DeliveryTracking> trackingPoints = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Create a new Delivery for an order (Legacy constructor preserved for backward compatibility).
     */
    public Delivery(UUID orderId) {
        this.id = UuidCreator.nextUuidV7();
        this.orderId = orderId;
        this.status = DeliveryStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Delivery(UUID orderId, Address pickupAddress, Address dropoffAddress, BigDecimal distanceKm) {
        this.id = UuidCreator.nextUuidV7();
        this.orderId = orderId;
        this.status = DeliveryStatus.PENDING;
        setPickupAddress(pickupAddress);
        setDropoffAddress(dropoffAddress);
        this.distanceKm = distanceKm;
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

    /**
     * Assign a driver to this delivery.
     */
    public void assignDriver(UUID driverId) {
        if (status != DeliveryStatus.PENDING && status != DeliveryStatus.FINDING_DRIVER) {
            throw new InvalidDeliveryStateException(status);
        }
        this.driverId = driverId;
        this.status = DeliveryStatus.DRIVER_ASSIGNED;
        this.driverAssignedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void pickUp() {
        requireStatus(DeliveryStatus.DRIVER_ASSIGNED);
        this.status = DeliveryStatus.PICKED_UP;
        this.pickedUpAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void startDelivering() {
        requireStatus(DeliveryStatus.PICKED_UP);
        this.status = DeliveryStatus.DELIVERING;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        requireStatus(DeliveryStatus.DELIVERING);
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        if (status == DeliveryStatus.DELIVERED) {
            throw new InvalidDeliveryStateException(status);
        }
        this.status = DeliveryStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void addTrackingPoint(BigDecimal lat, BigDecimal lng) {
        if (status != DeliveryStatus.DRIVER_ASSIGNED && status != DeliveryStatus.PICKED_UP && status != DeliveryStatus.DELIVERING) {
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
}

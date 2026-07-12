package com.fooddelivery.delivery.domain.model;

import com.fooddelivery.delivery.domain.exception.DriverNotEligibleException;
import com.fooddelivery.delivery.domain.model.valueobject.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.delivery.domain.util.UuidCreator;

/**
 * Driver aggregate — represents a delivery driver/shipper.
 */
@Entity
@Table(name = "drivers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Driver {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    private BigDecimal avgRating;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DriverStatus status;

    @Column(name = "is_online", nullable = false)
    private boolean isOnline;

    @Column(name = "current_latitude", precision = 9, scale = 6)
    private BigDecimal currentLatitude;

    @Column(name = "current_longitude", precision = 9, scale = 6)
    private BigDecimal currentLongitude;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    @Column(name = "total_reviews", nullable = false)
    private int totalReviews;

    public Driver(String fullName, String phone, VehicleType vehicleType,
                  String licensePlate, BigDecimal avgRating) {
        this.id = UuidCreator.nextUuidV7();
        this.fullName = fullName;
        this.phone = phone;
        this.vehicleType = vehicleType;
        this.licensePlate = licensePlate;
        this.avgRating = avgRating;
        this.available = true;
        this.status = DriverStatus.ACTIVE;
        this.isOnline = false;
        this.totalReviews = 0;
    }

    public void reserveForDelivery() {
        if (!available || !isOnline || status != DriverStatus.ACTIVE) {
            throw new DriverNotEligibleException(this.id, this.status);
        }
        this.available = false;
    }

    public void markAvailable() {
        this.available = true;
    }

    public VehicleInfo getVehicleInfo() {
        return new VehicleInfo(this.vehicleType, this.licensePlate);
    }

    public Rating getAvgRatingVO() {
        return this.avgRating != null ? new Rating(this.avgRating) : null;
    }

    public GeoLocation getCurrentLocation() {
        if (this.currentLatitude == null || this.currentLongitude == null) {
            return null;
        }
        return new GeoLocation(this.currentLatitude, this.currentLongitude);
    }

    public void activate() {
        this.status = DriverStatus.ACTIVE;
    }

    public void suspend() {
        this.status = DriverStatus.SUSPENDED;
        this.isOnline = false;
        this.available = false;
    }

    public void goOnline() {
        if (status != DriverStatus.ACTIVE) {
            throw new DriverNotEligibleException(this.id, status);
        }
        this.isOnline = true;
    }

    public void goOffline() {
        this.isOnline = false;
        // Stay unavailable if still on an active delivery; otherwise free for reassignment.
        if (this.available) {
            // no-op: already free
        }
    }

    public void linkUser(UUID userId) {
        this.userId = userId;
    }

    public void updateLocation(BigDecimal latitude, BigDecimal longitude) {
        this.currentLatitude = latitude;
        this.currentLongitude = longitude;
        this.locationUpdatedAt = Instant.now();
    }

    public void updateRating(BigDecimal newAvg, int totalReviews) {
        this.avgRating = newAvg;
        this.totalReviews = totalReviews;
    }
}

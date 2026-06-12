package com.fooddelivery.delivery.domain.model;

import com.fooddelivery.delivery.domain.model.valueobject.VehicleType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

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

    public Driver(String fullName, String phone, VehicleType vehicleType,
                  String licensePlate, BigDecimal avgRating) {
        this.id = UUID.randomUUID();
        this.fullName = fullName;
        this.phone = phone;
        this.vehicleType = vehicleType;
        this.licensePlate = licensePlate;
        this.avgRating = avgRating;
        this.available = true;
    }

    public void markUnavailable() {
        this.available = false;
    }

    public void markAvailable() {
        this.available = true;
    }
}

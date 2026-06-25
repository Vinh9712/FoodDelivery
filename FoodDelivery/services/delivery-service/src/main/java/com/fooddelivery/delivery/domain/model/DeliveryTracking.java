package com.fooddelivery.delivery.domain.model;

import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.delivery.domain.util.UuidCreator;

@Entity
@Table(name = "delivery_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryTracking {

    @Id
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_snapshot", nullable = false, length = 30)
    private DeliveryStatus statusSnapshot;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public DeliveryTracking(UUID id, UUID deliveryId, BigDecimal latitude, BigDecimal longitude, DeliveryStatus statusSnapshot) {
        this.id = id;
        this.deliveryId = deliveryId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.statusSnapshot = statusSnapshot;
        this.recordedAt = Instant.now();
    }

    public static DeliveryTracking of(UUID deliveryId, BigDecimal latitude, BigDecimal longitude, DeliveryStatus statusSnapshot) {
        return new DeliveryTracking(UuidCreator.nextUuidV7(), deliveryId, latitude, longitude, statusSnapshot);
    }
}

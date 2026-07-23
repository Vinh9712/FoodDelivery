package com.fooddelivery.delivery.domain.model;

import com.fooddelivery.delivery.domain.model.valueobject.Rating;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.delivery.domain.util.UuidCreator;

@Entity
@Table(name = "driver_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverReview {

    @Id
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(name = "comment")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DriverReview(UUID id, UUID driverId, UUID customerId, UUID orderId, Rating rating, String comment) {
        this.id = id;
        this.driverId = driverId;
        this.customerId = customerId;
        this.orderId = orderId;
        this.rating = rating.value();
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public static DriverReview submit(UUID driverId, UUID customerId, UUID orderId, Rating rating, String comment) {
        return new DriverReview(UuidCreator.nextUuidV7(), driverId, customerId, orderId, rating, comment);
    }

    public Rating getRating() {
        return new Rating(this.rating);
    }
}

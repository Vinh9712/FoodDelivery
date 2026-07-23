package com.fooddelivery.restaurant.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fooddelivery.restaurant.domain.exception.InvalidRestaurantStateException;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Restaurant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "address_line", nullable = false, columnDefinition = "TEXT")
    private String addressLine;

    private String district;

    @Column(nullable = false)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RestaurantStatus status;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "avg_rating", nullable = false)
    private BigDecimal avgRating;

    @Column(name = "total_reviews", nullable = false)
    private Integer totalReviews;

    @Column(name = "min_order_amount", nullable = false)
    private BigDecimal minOrderAmount;

    @Column(name = "estimated_delivery_time_min")
    private Integer estimatedDeliveryTimeMin;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "banner_url")
    private String bannerUrl;

    @Column(name = "is_accepting_orders")
    private Boolean isAcceptingOrders;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (avgRating == null) avgRating = BigDecimal.ZERO;
        if (totalReviews == null) totalReviews = 0;
        if (minOrderAmount == null) minOrderAmount = BigDecimal.ZERO;
        if (estimatedDeliveryTimeMin == null) estimatedDeliveryTimeMin = 30;
        if (isAcceptingOrders == null) isAcceptingOrders = false;
        if (status == null) status = RestaurantStatus.PENDING;
    }

    public boolean canAcceptOrders(LocalTime now) {
        if (status != RestaurantStatus.ACTIVE || !Boolean.TRUE.equals(isAcceptingOrders)
                || openTime == null || closeTime == null || now == null) {
            return false;
        }
        if (openTime.equals(closeTime)) {
            return true;
        }
        if (openTime.isBefore(closeTime)) {
            return !now.isBefore(openTime) && now.isBefore(closeTime);
        }
        return !now.isBefore(openTime) || now.isBefore(closeTime);
    }

    public void setAcceptingOrders(boolean accepting) {
        if (accepting && status != RestaurantStatus.ACTIVE) {
            throw new InvalidRestaurantStateException("Only ACTIVE restaurants can accept orders");
        }
        this.isAcceptingOrders = accepting;
    }

    public void changeStatus(RestaurantStatus next) {
        if (next == null) {
            throw new InvalidRestaurantStateException("status is required");
        }
        if (next == status) {
            return;
        }
        boolean allowed = (next == RestaurantStatus.SUSPENDED && status != RestaurantStatus.SUSPENDED)
                || (status == RestaurantStatus.SUSPENDED && next == RestaurantStatus.INACTIVE)
                // Admin reinstate: suspended → active (hours still required below)
                || (status == RestaurantStatus.SUSPENDED && next == RestaurantStatus.ACTIVE)
                || (next == RestaurantStatus.ACTIVE
                && (status == RestaurantStatus.PENDING || status == RestaurantStatus.INACTIVE))
                || (status == RestaurantStatus.ACTIVE && next == RestaurantStatus.INACTIVE);
        if (!allowed) {
            throw new InvalidRestaurantStateException(
                    "Invalid restaurant status transition: " + status + " -> " + next);
        }
        if (next == RestaurantStatus.ACTIVE && (openTime == null || closeTime == null)) {
            throw new InvalidRestaurantStateException("Business hours are required before activation");
        }
        this.status = next;
        if (next != RestaurantStatus.ACTIVE) {
            this.isAcceptingOrders = false;
        }
    }
}

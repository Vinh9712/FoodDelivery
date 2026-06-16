package com.fooddelivery.restaurant.domain.model;

import com.fooddelivery.restaurant.domain.exception.CategoryNotEmptyException;
import com.fooddelivery.restaurant.domain.exception.InvalidRestaurantStateException;
import com.fooddelivery.restaurant.domain.model.valueobject.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.fooddelivery.restaurant.domain.util.UuidCreator;

@Entity
@Table(name = "restaurants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Restaurant {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(name = "district", nullable = false)
    private String district;

    @Column(name = "city", nullable = false)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RestaurantStatus status;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating;

    @Column(name = "total_reviews", nullable = false)
    private int totalReviews;

    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "estimated_delivery_time_min", nullable = false)
    private int estimatedDeliveryTimeMin;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private List<MenuCategory> categories = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Restaurant(UUID id, UUID ownerId, String name, String description, String phone,
                      String addressLine, String district, String city,
                      OpeningHours openingHours, Money minOrderAmount, int estimatedDeliveryTimeMin) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.phone = phone;
        this.addressLine = addressLine;
        this.district = district;
        this.city = city;
        this.status = RestaurantStatus.PENDING;
        this.openTime = openingHours.openTime();
        this.closeTime = openingHours.closeTime();
        this.avgRating = BigDecimal.ZERO;
        this.totalReviews = 0;
        this.minOrderAmount = minOrderAmount.amount();
        this.estimatedDeliveryTimeMin = estimatedDeliveryTimeMin;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Restaurant create(UUID ownerId, String name, String description, String phone,
                                    String addressLine, String district, String city,
                                    OpeningHours openingHours, Money minOrderAmount, int estimatedDeliveryTimeMin) {
        return new Restaurant(UuidCreator.nextUuidV7(), ownerId, name, description, phone, addressLine, district, city, openingHours, minOrderAmount, estimatedDeliveryTimeMin);
    }

    public OpeningHours getOpeningHours() {
        return new OpeningHours(this.openTime, this.closeTime);
    }

    public Rating getAvgRating() {
        return new Rating(this.avgRating);
    }

    public Money getMinOrderAmount() {
        return new Money(this.minOrderAmount);
    }

    public void activate() {
        if (status == RestaurantStatus.CLOSED) {
            throw new InvalidRestaurantStateException("Cannot reactivate a closed restaurant");
        }
        this.status = RestaurantStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void suspend() {
        if (status != RestaurantStatus.ACTIVE) {
            throw new InvalidRestaurantStateException("Can only suspend an active restaurant");
        }
        this.status = RestaurantStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void close() {
        this.status = RestaurantStatus.CLOSED;
        this.updatedAt = Instant.now();
    }

    public MenuCategory addCategory(String name, String description) {
        int nextOrder = categories.stream()
                .mapToInt(MenuCategory::getSortOrder)
                .max()
                .orElse(-1) + 1;
        MenuCategory category = MenuCategory.create(this.id, name, description, nextOrder);
        categories.add(category);
        this.updatedAt = Instant.now();
        return category;
    }

    public void removeCategory(UUID categoryId) {
        MenuCategory category = findCategory(categoryId);
        if (category.hasAvailableItems()) {
            throw new CategoryNotEmptyException(categoryId);
        }
        categories.remove(category);
        this.updatedAt = Instant.now();
    }

    public void addMenuItem(UUID categoryId, MenuItem item) {
        findCategory(categoryId).addMenuItem(item);
        this.updatedAt = Instant.now();
    }

    public void toggleItemAvailability(UUID itemId) {
        findCategoryContaining(itemId).toggleItemAvailability(itemId);
        this.updatedAt = Instant.now();
    }

    private MenuCategory findCategory(UUID categoryId) {
        return categories.stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Category not found: " + categoryId));
    }

    private MenuCategory findCategoryContaining(UUID itemId) {
        return categories.stream()
                .filter(c -> c.getItems().stream().anyMatch(i -> i.getId().equals(itemId)))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("MenuItem not found in any category: " + itemId));
    }
}

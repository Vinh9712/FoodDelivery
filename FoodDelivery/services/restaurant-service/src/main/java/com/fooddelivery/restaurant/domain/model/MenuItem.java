package com.fooddelivery.restaurant.domain.model;

import com.fooddelivery.restaurant.domain.model.valueobject.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.restaurant.domain.util.UuidCreator;

@Entity
@Table(name = "menu_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuItem {

    @Id
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "prep_time_minutes", nullable = false)
    private int prepTimeMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MenuItem(UUID id, UUID categoryId, String name, String description, Money price, boolean isAvailable, String imageUrl, int prepTimeMinutes) {
        this.id = id;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.price = price.amount();
        this.isAvailable = isAvailable;
        this.imageUrl = imageUrl;
        this.prepTimeMinutes = prepTimeMinutes;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static MenuItem create(UUID categoryId, String name, String description, Money price, String imageUrl, int prepTimeMinutes) {
        return new MenuItem(UuidCreator.nextUuidV7(), categoryId, name, description, price, true, imageUrl, prepTimeMinutes);
    }

    public Money getPrice() {
        return new Money(this.price);
    }

    public void updateDetails(String name, String description, Money price, String imageUrl, int prepTimeMinutes) {
        this.name = name;
        this.description = description;
        this.price = price.amount();
        this.imageUrl = imageUrl;
        this.prepTimeMinutes = prepTimeMinutes;
        this.updatedAt = Instant.now();
    }

    public void toggleAvailability() {
        this.isAvailable = !this.isAvailable;
        this.updatedAt = Instant.now();
    }
}

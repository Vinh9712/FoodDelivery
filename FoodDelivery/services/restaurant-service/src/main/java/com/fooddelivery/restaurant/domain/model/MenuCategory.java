package com.fooddelivery.restaurant.domain.model;

import com.fooddelivery.restaurant.domain.model.valueobject.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.fooddelivery.restaurant.domain.util.UuidCreator;

@Entity
@Table(name = "menu_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuCategory {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private List<MenuItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MenuCategory(UUID id, UUID restaurantId, String name, String description, int sortOrder, boolean isAvailable) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
        this.isAvailable = isAvailable;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static MenuCategory create(UUID restaurantId, String name, String description, int sortOrder) {
        return new MenuCategory(UuidCreator.nextUuidV7(), restaurantId, name, description, sortOrder, true);
    }

    public boolean hasAvailableItems() {
        return items.stream().anyMatch(MenuItem::isAvailable);
    }

    public void addMenuItem(MenuItem item) {
        this.items.add(item);
        this.updatedAt = Instant.now();
    }

    public void toggleItemAvailability(UUID itemId) {
        MenuItem item = items.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("MenuItem not found: " + itemId));
        item.toggleAvailability();
        this.updatedAt = Instant.now();
    }

    public MenuItem findMenuItem(UUID itemId) {
        return items.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("MenuItem not found: " + itemId));
    }
}

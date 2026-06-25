package com.fooddelivery.order.domain.model;

import com.fooddelivery.order.domain.model.valueobject.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;
import com.fooddelivery.order.domain.util.UuidCreator;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    public OrderItem(UUID id, UUID orderId, UUID menuItemId, String name, Money price, int quantity) {
        this.id = id;
        this.orderId = orderId;
        this.menuItemId = menuItemId;
        this.name = name;
        this.price = price.amount();
        this.quantity = quantity;
    }

    public static OrderItem create(UUID orderId, UUID menuItemId, String name, Money price, int quantity) {
        return new OrderItem(UuidCreator.nextUuidV7(), orderId, menuItemId, name, price, quantity);
    }

    public Money getPrice() {
        return new Money(this.price);
    }

    public Money getSubtotal() {
        return new Money(this.price.multiply(BigDecimal.valueOf(quantity)));
    }
}

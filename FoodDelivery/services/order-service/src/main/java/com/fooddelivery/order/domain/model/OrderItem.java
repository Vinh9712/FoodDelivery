package com.fooddelivery.order.domain.model;

import com.fooddelivery.order.domain.model.valueobject.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fooddelivery.order.domain.util.UuidCreator;

/**
 * OrderItem entity — dòng hàng trong đơn hàng.
 * <p>
 * Đóng gói toàn bộ thông tin dòng hàng. Khởi tạo bắt buộc thông qua
 * constructor được gọi nội bộ từ Aggregate Root {@link Order}.
 * </p>
 */
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

    /** Tên món ăn (snapshot tại thời điểm đặt hàng) */
    @Column(name = "item_name", nullable = false)
    private String itemName;

    /** Mô tả món ăn */
    @Column(name = "item_description")
    private String itemDescription;

    /** Đơn giá tại thời điểm đặt hàng */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** Thành tiền = unit_price × quantity (tính sẵn để tránh tính toán lặp) */
    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Constructor nội bộ — chỉ được gọi từ Order aggregate root (backward compatible).
     */
    public OrderItem(UUID id, UUID orderId, UUID menuItemId, String itemName,
                     Money unitPrice, int quantity) {
        this.id = id;
        this.orderId = orderId;
        this.menuItemId = menuItemId;
        this.itemName = itemName;
        this.unitPrice = unitPrice.amount();
        this.quantity = quantity;
        this.subtotal = this.unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.createdAt = Instant.now();
    }

    /**
     * Factory method cho Saga flow — tạo OrderItem với subtotal tính sẵn.
     */
    static OrderItem createForSaga(UUID orderId, UUID menuItemId, String itemName,
                                   String itemDescription, BigDecimal unitPrice,
                                   int quantity, BigDecimal subtotal) {
        var item = new OrderItem();
        item.id = UuidCreator.nextUuidV7();
        item.orderId = orderId;
        item.menuItemId = menuItemId;
        item.itemName = itemName;
        item.itemDescription = itemDescription;
        item.unitPrice = unitPrice;
        item.quantity = quantity;
        item.subtotal = subtotal;
        item.createdAt = Instant.now();
        return item;
    }

    /**
     * Factory method tạo OrderItem (backward compatible).
     */
    public static OrderItem create(UUID orderId, UUID menuItemId, String name,
                                   Money price, int quantity) {
        return new OrderItem(UuidCreator.nextUuidV7(), orderId, menuItemId, name, price, quantity);
    }

    /** Lấy đơn giá dạng Money value object */
    public Money getUnitPriceMoney() {
        return new Money(this.unitPrice);
    }

    /** Lấy thành tiền dạng Money value object */
    public Money getSubtotalMoney() {
        return new Money(this.subtotal);
    }

    // Backward compatibility aliases
    public UUID getId() { return this.id; }
    public UUID getOrderId() { return this.orderId; }
    public UUID getMenuItemId() { return this.menuItemId; }
    public String getItemDescription() { return this.itemDescription; }
    public int getQuantity() { return this.quantity; }
    public BigDecimal getSubtotal() { return this.subtotal; }
    public Instant getCreatedAt() { return this.createdAt; }
    public BigDecimal getUnitPrice() { return this.unitPrice; }
    public String getItemName() { return this.itemName; }
}

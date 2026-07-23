package com.fooddelivery.order.api.mapper;

import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.OrderItem;
import com.fooddelivery.order.domain.model.valueobject.DeliveryAddressSnapshot;
import com.fooddelivery.order.domain.model.valueobject.Money;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperTest {

    private final OrderMapper mapper = new OrderMapper();

    @Test
    void mapsItemsAddressAndHistory() {
        UUID customerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID menuItemId = UUID.randomUUID();
        DeliveryAddressSnapshot address = new DeliveryAddressSnapshot(
                "12 Nguyen Hue", "Q1", "HCM", BigDecimal.valueOf(10.7), BigDecimal.valueOf(106.7));
        OrderItem item = OrderItem.create(
                UUID.randomUUID(), menuItemId, "Pho Bo", new Money(BigDecimal.valueOf(50_000)), 2);
        Order order = Order.create(
                customerId,
                restaurantId,
                List.of(item),
                address,
                new Money(BigDecimal.valueOf(10_000)),
                new Money(BigDecimal.ZERO),
                null,
                "no chili");

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.note()).isEqualTo("no chili");
        assertThat(response.subtotal()).isEqualByComparingTo("100000");
        assertThat(response.deliveryFee()).isEqualByComparingTo("10000");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().name()).isEqualTo("Pho Bo");
        assertThat(response.items().getFirst().quantity()).isEqualTo(2);
        assertThat(response.deliveryAddress().addressLine()).isEqualTo("12 Nguyen Hue");
        assertThat(response.deliveryAddress().city()).isEqualTo("HCM");
        assertThat(response.statusHistory()).isNotEmpty();
        assertThat(response.statusHistory().getFirst().toStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.paymentStatus()).isNotNull();
        assertThat(response.refundStatus()).isNotNull();
    }
}

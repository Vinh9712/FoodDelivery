package com.fooddelivery.order.api;

import com.fooddelivery.order.api.controller.InternalOrderController;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalOrderControllerTests {

    @Mock
    private OrderRepository orderRepository;

    private InternalOrderController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalOrderController(orderRepository);
    }

    @Test
    void reviewEligibilityRequiresMatchingDeliveredOrder() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        Order order = mock(Order.class);
        when(order.getCustomerId()).thenReturn(customerId);
        when(order.getRestaurantId()).thenReturn(restaurantId);
        when(order.getStatus()).thenReturn(OrderStatus.DELIVERED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        var eligible = controller.reviewEligibility(orderId, customerId, restaurantId);
        var wrongCustomer = controller.reviewEligibility(orderId, UUID.randomUUID(), restaurantId);

        assertThat(eligible.eligible()).isTrue();
        assertThat(wrongCustomer.eligible()).isFalse();
        assertThat(wrongCustomer.reason()).contains("customer");
    }
}

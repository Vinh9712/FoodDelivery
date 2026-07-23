package com.fooddelivery.order.application;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.saga.OrderCompensationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderCompensationService compensationService;

    private CustomerOrderService service;

    @BeforeEach
    void setUp() {
        service = new CustomerOrderService(orderRepository, compensationService);
    }

    @Test
    void listScopesEveryQueryToTheAuthenticatedCustomer() {
        UUID customerId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Order order = org.mockito.Mockito.mock(Order.class);
        var page = new PageImpl<>(List.of(order), pageable, 1);
        when(orderRepository.findByCustomerId(customerId, pageable)).thenReturn(page);
        when(orderRepository.findByCustomerIdAndStatus(
                customerId, OrderStatus.PAID, pageable)).thenReturn(page);

        assertThat(service.list(customerId, null, pageable).getContent()).containsExactly(order);
        assertThat(service.list(customerId, OrderStatus.PAID, pageable).getContent())
                .containsExactly(order);

        verify(orderRepository).findByCustomerId(customerId, pageable);
        verify(orderRepository).findByCustomerIdAndStatus(customerId, OrderStatus.PAID, pageable);
    }

    @Test
    void cancelUsesTheCustomerCancellationAuditCodeAndReloadsTheResult() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order before = org.mockito.Mockito.mock(Order.class);
        Order after = org.mockito.Mockito.mock(Order.class);
        when(orderRepository.findByIdAndCustomerId(orderId, customerId))
                .thenReturn(Optional.of(before), Optional.of(after));

        Order result = service.cancel(orderId, customerId, "Customer changed delivery plans");

        assertThat(result).isSameAs(after);
        verify(compensationService).start(
                orderId,
                CancellationCode.CUSTOMER_REQUESTED,
                "Customer changed delivery plans",
                OrderEventPayloads.Source.CUSTOMER);
    }

    @Test
    void cancelHidesForeignAndMissingOrdersAsNotFound() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(orderRepository.findByIdAndCustomerId(orderId, customerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(orderId, customerId, "Changed plans"))
                .isInstanceOf(OrderNotFoundException.class);
        verify(compensationService, never()).start(any(), any(), any(), any());
    }

    @Test
    void cancelPropagatesAStateConflictWhenRestaurantWinsTheRace() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(orderRepository.findByIdAndCustomerId(orderId, customerId))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Order.class)));
        doThrow(new InvalidOrderStateException("Customer cancellation requires PAID"))
                .when(compensationService).start(any(), any(), any(), any());

        assertThatThrownBy(() -> service.cancel(orderId, customerId, "Changed plans"))
                .isInstanceOf(InvalidOrderStateException.class);
    }
}

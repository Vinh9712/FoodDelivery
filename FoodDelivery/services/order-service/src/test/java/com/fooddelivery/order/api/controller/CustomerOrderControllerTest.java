package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.api.dto.CancelOrderRequest;
import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.api.mapper.OrderMapper;
import com.fooddelivery.order.application.CustomerOrderService;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOrderControllerTest {

    @Mock
    private CustomerOrderService customerOrderService;
    @Mock
    private OrderMapper orderMapper;

    private CustomerOrderController controller;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new CustomerOrderController(customerOrderService, orderMapper);
    }

    @Test
    void listUsesJwtSubjectAndReturnsAStandardSpringPage() {
        var pageable = PageRequest.of(0, 20);
        Order order = org.mockito.Mockito.mock(Order.class);
        when(customerOrderService.list(customerId, null, pageable))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderMapper.toResponse(order)).thenReturn(response(OrderStatus.PAID));

        ResponseEntity<?> result = controller.list(null, pageable, customerAuth());

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isInstanceOf(org.springframework.data.domain.Page.class);
        verify(customerOrderService).list(customerId, null, pageable);
    }

    @Test
    void cancelUsesJwtSubjectAndReturnsTheRefundWorkflowState() {
        Order order = org.mockito.Mockito.mock(Order.class);
        when(customerOrderService.cancel(orderId, customerId, "Changed delivery plans"))
                .thenReturn(order);
        when(orderMapper.toResponse(order))
                .thenReturn(response(OrderStatus.CANCELLATION_PENDING));

        var result = controller.cancel(
                orderId,
                new CancelOrderRequest("Changed delivery plans"),
                customerAuth());

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().status()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        verify(customerOrderService).cancel(orderId, customerId, "Changed delivery plans");
    }

    @Test
    void cancellationReasonIsRequiredAndBounded() {
        assertThat(validator.validate(new CancelOrderRequest(null))).isNotEmpty();
        assertThat(validator.validate(new CancelOrderRequest("   "))).isNotEmpty();
        assertThat(validator.validate(new CancelOrderRequest("x".repeat(501)))).isNotEmpty();
        assertThat(validator.validate(new CancelOrderRequest("Changed plans"))).isEmpty();
    }

    @Test
    void optimisticRaceIsMappedToConflict() {
        var problem = new GlobalExceptionHandler().handleOptimisticLockingFailure(
                new OptimisticLockingFailureException("Concurrent order update"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Order State Conflict");
    }

    private Authentication customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                customerId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private OrderResponse response(OrderStatus status) {
        return new OrderResponse(
                orderId,
                customerId,
                UUID.randomUUID(),
                status,
                BigDecimal.valueOf(125_000),
                null,
                PaymentStatus.PAID,
                status == OrderStatus.CANCELLATION_PENDING
                        ? RefundStatus.PENDING
                        : RefundStatus.NOT_REQUIRED,
                Instant.parse("2026-07-23T00:00:00Z"),
                Instant.parse("2026-07-23T00:10:00Z"),
                Instant.parse("2026-07-23T00:00:00Z"),
                Instant.parse("2026-07-23T00:00:00Z"));
    }
}

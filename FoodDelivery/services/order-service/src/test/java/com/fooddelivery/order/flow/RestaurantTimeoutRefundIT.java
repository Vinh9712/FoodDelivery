package com.fooddelivery.order.flow;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import com.fooddelivery.order.infrastructure.client.DeliveryServiceClient;
import com.fooddelivery.order.infrastructure.client.NotificationServiceClient;
import com.fooddelivery.order.infrastructure.client.PaymentServiceClient;
import com.fooddelivery.order.infrastructure.client.RestaurantServiceClient;
import com.fooddelivery.order.infrastructure.client.dto.RefundRequest;
import com.fooddelivery.order.infrastructure.client.dto.RefundResponse;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.saga.OrderCompensationService;
import com.fooddelivery.order.saga.RestaurantAcceptanceTimeoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Restaurant timeout starts refund; first refund attempt times out; later attempt confirms CANCELLED once.
 */
@SpringBootTest
@ActiveProfiles("test")
class RestaurantTimeoutRefundIT {

    private static final Instant PAID_AT = Instant.parse("2026-07-22T09:00:00Z");
    private static final Instant AFTER_DEADLINE = PAID_AT.plus(Duration.ofMinutes(11));

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RestaurantAcceptanceTimeoutService timeoutService;

    @Autowired
    private OrderCompensationService compensationService;

    @MockBean
    private PaymentServiceClient paymentServiceClient;
    @MockBean
    private DeliveryServiceClient deliveryServiceClient;
    @MockBean
    private NotificationServiceClient notificationServiceClient;
    @MockBean
    private RestaurantServiceClient restaurantServiceClient;

    @Test
    void timeoutThenLateRefundConfirmsCancelledOnce() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(42_000));
        order.markPaid(PAID_AT, Duration.ofMinutes(10));
        order = orderRepository.saveAndFlush(order);
        UUID orderId = order.getId();

        AtomicInteger calls = new AtomicInteger();
        when(paymentServiceClient.refundPayment(anyString(), any(RefundRequest.class)))
                .thenAnswer(inv -> {
                    int n = calls.incrementAndGet();
                    RefundRequest req = inv.getArgument(1);
                    if (n == 1) {
                        throw new RuntimeException("read timed out");
                    }
                    return new RefundResponse(
                            req.orderId(), "REFUNDED", "ok",
                            UUID.randomUUID(), UUID.randomUUID(),
                            req.amount(), AFTER_DEADLINE.plusSeconds(30));
                });

        assertThat(timeoutService.processCandidate(orderId))
                .isEqualTo(RestaurantAcceptanceTimeoutService.Outcome.TIMED_OUT);

        Order afterTimeout = orderRepository.findById(orderId).orElseThrow();
        assertThat(afterTimeout.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        assertThat(afterTimeout.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(afterTimeout.getRefundStatus()).isEqualTo(RefundStatus.PENDING);

        // Retry reconciliation after payment commit (lookup/event not needed — REST succeeds)
        compensationService.reconcileRefund(orderId);

        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(finalOrder.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(finalOrder.getRefundStatus()).isEqualTo(RefundStatus.SUCCEEDED);

        // Second reconcile is idempotent
        compensationService.reconcileRefund(orderId);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(calls.get()).isEqualTo(2);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock restaurantTimeoutItClock() {
            return Clock.fixed(AFTER_DEADLINE, ZoneOffset.UTC);
        }
    }
}

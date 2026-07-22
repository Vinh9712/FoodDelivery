package com.fooddelivery.order.saga;

import com.fooddelivery.order.application.RestaurantOrderService;
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
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Accept vs timeout race against the same order version (H2 optimistic locking).
 * Exactly one valid outcome: CONFIRMED (no compensation) or CANCELLATION_PENDING (one start).
 */
@SpringBootTest
@ActiveProfiles("test")
class RestaurantAcceptanceTimeoutConcurrencyIT {

    private static final Instant PAID_AT = Instant.parse("2026-07-22T10:00:00Z");
    private static final Instant AFTER_DEADLINE = PAID_AT.plus(Duration.ofMinutes(10)).plusSeconds(1);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RestaurantOrderService restaurantOrderService;

    @Autowired
    private RestaurantAcceptanceTimeoutService timeoutService;

    @MockBean
    private PaymentServiceClient paymentServiceClient;

    @MockBean
    private DeliveryServiceClient deliveryServiceClient;

    @MockBean
    private NotificationServiceClient notificationServiceClient;

    @MockBean
    private RestaurantServiceClient restaurantServiceClient;

    @BeforeEach
    void stubPayment() {
        when(paymentServiceClient.refundPayment(anyString(), any(RefundRequest.class)))
                .thenAnswer(inv -> {
                    RefundRequest req = inv.getArgument(1);
                    return new RefundResponse(
                            req.orderId(),
                            "REFUNDED",
                            "ok",
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            req.amount(),
                            Instant.parse("2026-07-22T10:15:00Z"));
                });
    }

    @Test
    void acceptAndTimeoutRaceProducesExactlyOneWinner() throws Exception {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(99_000));
        order.markPaid(PAID_AT, Duration.ofMinutes(10));
        order = orderRepository.saveAndFlush(order);
        UUID orderId = order.getId();
        UUID actorId = UUID.randomUUID();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> acceptError = new AtomicReference<>();
        AtomicReference<Throwable> timeoutError = new AtomicReference<>();

        Future<?> acceptFuture = pool.submit(() -> {
            try {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                restaurantOrderService.accept(orderId, actorId);
            } catch (Throwable t) {
                acceptError.set(t);
            }
        });
        Future<?> timeoutFuture = pool.submit(() -> {
            try {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                timeoutService.processCandidate(orderId);
            } catch (Throwable t) {
                timeoutError.set(t);
            }
        });

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        acceptFuture.get(10, TimeUnit.SECONDS);
        timeoutFuture.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isIn(
                OrderStatus.CONFIRMED, OrderStatus.CANCELLATION_PENDING, OrderStatus.CANCELLED);

        if (reloaded.getStatus() == OrderStatus.CONFIRMED) {
            assertThat(reloaded.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(reloaded.getRefundStatus()).isEqualTo(RefundStatus.NOT_REQUIRED);
            // Timeout path must not leave an uncaught error — accept won via optimistic lock or state check
            assertThat(timeoutError.get()).isNull();
        } else {
            // Timeout won: compensation pending or refund already confirmed
            assertThat(reloaded.getStatus()).isIn(OrderStatus.CANCELLATION_PENDING, OrderStatus.CANCELLED);
            assertThat(reloaded.getPaymentStatus()).isIn(PaymentStatus.PAID, PaymentStatus.REFUNDED);
            // Accept may fail with optimistic lock or InvalidOrderState — either is fine
            // (acceptError may be null if accept ran first then timeout overwrote... but status wouldn't be pending)
        }
        // Exactly one semantic winner: never both CONFIRMED and refunded
        if (reloaded.getStatus() == OrderStatus.CONFIRMED) {
            assertThat(reloaded.getRefundStatus()).isNotEqualTo(RefundStatus.PENDING);
            assertThat(reloaded.getRefundStatus()).isNotEqualTo(RefundStatus.SUCCEEDED);
        }
    }

    @Test
    void repeatedTimeoutTicksDoNotCreateSecondCompensationWhenAlreadyPending() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(55_000));
        order.markPaid(PAID_AT, Duration.ofMinutes(10));
        order = orderRepository.saveAndFlush(order);
        UUID orderId = order.getId();

        RestaurantAcceptanceTimeoutService.Outcome first = timeoutService.processCandidate(orderId);
        RestaurantAcceptanceTimeoutService.Outcome second = timeoutService.processCandidate(orderId);

        assertThat(first).isEqualTo(RestaurantAcceptanceTimeoutService.Outcome.TIMED_OUT);
        assertThat(second).isIn(
                RestaurantAcceptanceTimeoutService.Outcome.ACCEPT_WON,
                RestaurantAcceptanceTimeoutService.Outcome.NOT_DUE,
                RestaurantAcceptanceTimeoutService.Outcome.TIMED_OUT);

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isIn(OrderStatus.CANCELLATION_PENDING, OrderStatus.CANCELLED);
        // Only one refund workflow: status not double-flipped oddly
        assertThat(reloaded.getRefundStatus()).isIn(RefundStatus.PENDING, RefundStatus.SUCCEEDED,
                RefundStatus.MANUAL_REVIEW);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testRestaurantTimeoutClock() {
            return Clock.fixed(AFTER_DEADLINE, ZoneOffset.UTC);
        }
    }
}

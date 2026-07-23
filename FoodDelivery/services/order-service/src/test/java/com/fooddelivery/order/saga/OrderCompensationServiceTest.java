package com.fooddelivery.order.saga;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.PickupAddressSnapshot;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import com.fooddelivery.order.infrastructure.client.PaymentServiceClient;
import com.fooddelivery.order.infrastructure.client.dto.RefundRequest;
import com.fooddelivery.order.infrastructure.client.dto.RefundResponse;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCompensationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private PaymentServiceClient paymentClient;
    @Mock
    private PlatformTransactionManager transactionManager;

    private OrderCompensationService compensation;
    private Order order;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        compensation = new OrderCompensationService(
                orderRepository,
                outboxEventRepository,
                paymentClient,
                Clock.fixed(NOW, ZoneOffset.UTC),
                emptyMeterRegistry(),
                transactionManager,
                Duration.ofSeconds(30),
                Duration.ofMinutes(30),
                8);

        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> mock(TransactionStatus.class));
        order = readyOrder();
        orderId = order.getId();
        lenient().when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(outboxEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void timeoutLeavesCancellationPendingAndPaymentPaid() {
        RetryableException timeout = mock(RetryableException.class);
        when(timeout.getMessage()).thenReturn("read timed out");
        when(paymentClient.refundPayment(eq("refund:" + orderId), any(RefundRequest.class)))
                .thenThrow(timeout);

        compensation.start(orderId, CancellationCode.DELIVERY_FAILED, "no courier",
                OrderEventPayloads.Source.DELIVERY_EVENT);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getRefundStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(order.getRefundAttempts()).isEqualTo(1);
        assertThat(order.getNextRefundAttemptAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void confirmedRefundMakesOrderCancelled() {
        order.beginCompensation("no courier", CancellationCode.DELIVERY_FAILED,
                OrderEventPayloads.Source.DELIVERY_EVENT, NOW);
        order.clearPendingOutboxEvents();

        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        compensation.onRefundConfirmed(orderId, paymentId, refundId, order.getTotalAmount(), NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getRefundStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    }

    @Test
    void successfulRestRefundConfirmsImmediately() {
        when(paymentClient.refundPayment(eq("refund:" + orderId), any(RefundRequest.class)))
                .thenReturn(new RefundResponse(
                        orderId, "REFUNDED", "ok",
                        UUID.randomUUID(), UUID.randomUUID(),
                        order.getTotalAmount(), NOW));

        compensation.start(orderId, CancellationCode.DELIVERY_FAILED, "no courier",
                OrderEventPayloads.Source.DELIVERY_EVENT);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getRefundStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    }

    @Test
    void commitsCancellationPendingBeforeCallingRemoteRefund() {
        when(paymentClient.refundPayment(eq("refund:" + orderId), any(RefundRequest.class)))
                .thenReturn(new RefundResponse(
                        orderId, "REFUNDED", "ok",
                        UUID.randomUUID(), UUID.randomUUID(),
                        order.getTotalAmount(), NOW));

        compensation.start(orderId, CancellationCode.DELIVERY_FAILED, "no courier",
                OrderEventPayloads.Source.DELIVERY_EVENT);

        var calls = inOrder(orderRepository, outboxEventRepository, transactionManager, paymentClient);
        calls.verify(orderRepository).saveAndFlush(order);
        calls.verify(outboxEventRepository).saveAll(any());
        calls.verify(transactionManager).commit(any(TransactionStatus.class));
        calls.verify(paymentClient).refundPayment(
                eq("refund:" + orderId), any(RefundRequest.class));
    }

    @Test
    void commitRacePreventsRemoteRefundSideEffect() {
        order = paidOrder();
        orderId = order.getId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        var race = new OptimisticLockingFailureException("restaurant accepted first");
        when(orderRepository.saveAndFlush(order)).thenThrow(race);

        assertThatThrownBy(() -> compensation.start(
                orderId,
                CancellationCode.CUSTOMER_REQUESTED,
                "changed plans",
                OrderEventPayloads.Source.CUSTOMER))
                .isSameAs(race);

        verify(paymentClient, never()).refundPayment(any(), any());
        verify(outboxEventRepository, never()).saveAll(any());
    }

    @Test
    void startIsIdempotentWhenAlreadyPending() {
        order.beginCompensation("no courier", CancellationCode.DELIVERY_FAILED,
                OrderEventPayloads.Source.DELIVERY_EVENT, NOW);
        order.clearPendingOutboxEvents();
        when(paymentClient.refundPayment(any(), any())).thenThrow(new RuntimeException("still down"));

        compensation.start(orderId, CancellationCode.DELIVERY_FAILED, "again",
                OrderEventPayloads.Source.DELIVERY_EVENT);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void exhaustedRetriesMoveToManualReviewKeepingCancellationPending() {
        order.beginCompensation("no courier", CancellationCode.DELIVERY_FAILED,
                OrderEventPayloads.Source.DELIVERY_EVENT, NOW);
        order.scheduleFirstRefundAttempt(NOW);
        order.clearPendingOutboxEvents();
        when(paymentClient.getPaymentByOrderId(orderId))
                .thenReturn(new com.fooddelivery.order.infrastructure.client.dto.PaymentResponse(
                        orderId, "SUCCESS", "txn", "paid"));
        when(paymentClient.refundPayment(any(), any())).thenThrow(new RuntimeException("timeout"));

        for (int i = 0; i < 8; i++) {
            compensation.reconcileRefund(orderId);
        }

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        assertThat(order.getRefundStatus()).isEqualTo(RefundStatus.MANUAL_REVIEW);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getNextRefundAttemptAt()).isNull();
    }

    private Order readyOrder() {
        Order o = paidOrder();
        o.acceptByRestaurant(UUID.randomUUID());
        o.startPreparing(UUID.randomUUID());
        o.markReadyForPickup(UUID.randomUUID());
        o.clearPendingOutboxEvents();
        return o;
    }

    private Order paidOrder() {
        UUID customerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        PickupAddressSnapshot pickup = new PickupAddressSnapshot(
                restaurantId, "Pho 24", "0901000000", "12 Le Loi", null, null);
        Order o = Order.create(customerId, restaurantId, "1 Nguyen Hue",
                BigDecimal.valueOf(15000), BigDecimal.ZERO, "req-" + UUID.randomUUID(), pickup);
        o.addItem(UUID.randomUUID(), "Pho", "large", BigDecimal.valueOf(50000), 1);
        o.markPaid(Instant.parse("2026-07-22T10:00:00Z"), Duration.ofMinutes(10));
        o.clearPendingOutboxEvents();
        return o;
    }

    private static ObjectProvider<io.micrometer.core.instrument.MeterRegistry> emptyMeterRegistry() {
        return new ObjectProvider<>() {
            @Override
            public io.micrometer.core.instrument.MeterRegistry getObject(Object... args) {
                return null;
            }

            @Override
            public io.micrometer.core.instrument.MeterRegistry getIfAvailable() {
                return null;
            }

            @Override
            public io.micrometer.core.instrument.MeterRegistry getIfUnique() {
                return null;
            }

            @Override
            public io.micrometer.core.instrument.MeterRegistry getObject() {
                return null;
            }

            @Override
            public io.micrometer.core.instrument.MeterRegistry getIfAvailable(
                    Supplier<io.micrometer.core.instrument.MeterRegistry> defaultSupplier) {
                return defaultSupplier.get();
            }
        };
    }
}

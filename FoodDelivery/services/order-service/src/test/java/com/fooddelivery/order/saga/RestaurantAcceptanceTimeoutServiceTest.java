package com.fooddelivery.order.saga;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantAcceptanceTimeoutServiceTest {

    private static final Instant PAID_AT = Instant.parse("2026-07-22T12:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Instant DEADLINE = PAID_AT.plus(WINDOW);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderCompensationService compensationService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private SimpleMeterRegistry meterRegistry;
    private Order order;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        order = paidOrder(PAID_AT, WINDOW);
        orderId = order.getId();
        lenient().when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        // Synchronous TransactionTemplate: execute callback immediately without a real txn
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().doAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(1);
            return callback.doInTransaction(new SimpleTransactionStatus());
        }).when(transactionManager).getTransaction(any());
        // TransactionTemplate calls getTransaction + commit; hook execute path via PlatformTransactionManager
        lenient().doNothing().when(transactionManager).commit(any());
        lenient().doNothing().when(transactionManager).rollback(any());
    }

    @Test
    void beforeDeadlineDoesNotStartCompensation() {
        Clock clock = Clock.fixed(DEADLINE.minusSeconds(1), ZoneOffset.UTC);
        RestaurantAcceptanceTimeoutService service = service(clock);

        assertThat(order.requestCancellationIfRestaurantTimedOut(clock.instant())).isFalse();
        RestaurantAcceptanceTimeoutService.Outcome outcome = service.processCandidate(orderId);

        assertThat(outcome).isEqualTo(RestaurantAcceptanceTimeoutService.Outcome.NOT_DUE);
        verify(compensationService, never()).start(any(), any(), any(), any());
        assertThat(counter("timed_out")).isZero();
        assertThat(counter("accept_won")).isZero();
    }

    @Test
    void exactlyAtDeadlineTimesOutOnce() {
        Clock clock = Clock.fixed(DEADLINE, ZoneOffset.UTC);
        RestaurantAcceptanceTimeoutService service = service(clock);
        doAnswer(inv -> {
            Order o = orderRepository.findById(inv.getArgument(0)).orElseThrow();
            o.beginCompensation(
                    inv.getArgument(2),
                    inv.getArgument(1),
                    inv.getArgument(3),
                    clock.instant());
            return null;
        }).when(compensationService).start(any(), any(), any(), any());

        assertThat(order.requestCancellationIfRestaurantTimedOut(DEADLINE)).isTrue();
        RestaurantAcceptanceTimeoutService.Outcome outcome = service.processCandidate(orderId);

        assertThat(outcome).isEqualTo(RestaurantAcceptanceTimeoutService.Outcome.TIMED_OUT);
        verify(compensationService).start(
                eq(orderId),
                eq(CancellationCode.RESTAURANT_ACCEPTANCE_TIMEOUT),
                eq(RestaurantAcceptanceTimeoutService.REASON),
                eq(OrderEventPayloads.Source.SYSTEM_TIMEOUT));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        assertThat(order.getRefundStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(counter("timed_out")).isEqualTo(1.0);
    }

    @Test
    void afterDeadlineTimesOut() {
        Clock clock = Clock.fixed(DEADLINE.plusSeconds(30), ZoneOffset.UTC);
        RestaurantAcceptanceTimeoutService service = service(clock);
        doAnswer(inv -> {
            Order o = orderRepository.findById(inv.getArgument(0)).orElseThrow();
            o.beginCompensation(
                    inv.getArgument(2),
                    inv.getArgument(1),
                    inv.getArgument(3),
                    clock.instant());
            return null;
        }).when(compensationService).start(any(), any(), any(), any());

        RestaurantAcceptanceTimeoutService.Outcome outcome = service.processCandidate(orderId);

        assertThat(outcome).isEqualTo(RestaurantAcceptanceTimeoutService.Outcome.TIMED_OUT);
        assertThat(counter("timed_out")).isEqualTo(1.0);
    }

    @Test
    void acceptWonBeforeTimeoutDoesNotCompensate() {
        order.acceptByRestaurant(UUID.randomUUID());
        Clock clock = Clock.fixed(DEADLINE.plusSeconds(1), ZoneOffset.UTC);
        RestaurantAcceptanceTimeoutService service = service(clock);

        RestaurantAcceptanceTimeoutService.Outcome outcome = service.processCandidate(orderId);

        assertThat(outcome).isEqualTo(RestaurantAcceptanceTimeoutService.Outcome.ACCEPT_WON);
        verify(compensationService, never()).start(any(), any(), any(), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(counter("accept_won")).isEqualTo(1.0);
    }

    @Test
    void optimisticLockTreatsAcceptAsWinner() {
        Clock clock = Clock.fixed(DEADLINE.plusSeconds(1), ZoneOffset.UTC);
        RestaurantAcceptanceTimeoutService service = service(clock);
        doThrow(new ObjectOptimisticLockingFailureException(Order.class, orderId))
                .when(compensationService).start(any(), any(), any(), any());

        RestaurantAcceptanceTimeoutService.Outcome outcome = service.processCandidate(orderId);

        assertThat(outcome).isEqualTo(RestaurantAcceptanceTimeoutService.Outcome.ACCEPT_WON);
        assertThat(counter("accept_won")).isEqualTo(1.0);
        assertThat(counter("timed_out")).isZero();
    }

    @Test
    void invalidStateRaceTreatsAcceptAsWinner() {
        Clock clock = Clock.fixed(DEADLINE.plusSeconds(1), ZoneOffset.UTC);
        RestaurantAcceptanceTimeoutService service = service(clock);
        doThrow(new InvalidOrderStateException("Cannot request cancellation"))
                .when(compensationService).start(any(), any(), any(), any());

        RestaurantAcceptanceTimeoutService.Outcome outcome = service.processCandidate(orderId);

        assertThat(outcome).isEqualTo(RestaurantAcceptanceTimeoutService.Outcome.ACCEPT_WON);
        assertThat(counter("accept_won")).isEqualTo(1.0);
    }

    @Test
    void repeatedProcessDoesNotDoubleStartWhenAlreadyPending() {
        Clock clock = Clock.fixed(DEADLINE.plusSeconds(1), ZoneOffset.UTC);
        RestaurantAcceptanceTimeoutService service = service(clock);
        doAnswer(inv -> {
            Order o = orderRepository.findById(inv.getArgument(0)).orElseThrow();
            if (o.getStatus() == OrderStatus.PAID) {
                o.beginCompensation(
                        inv.getArgument(2),
                        inv.getArgument(1),
                        inv.getArgument(3),
                        clock.instant());
            }
            return null;
        }).when(compensationService).start(any(), any(), any(), any());

        assertThat(service.processCandidate(orderId))
                .isEqualTo(RestaurantAcceptanceTimeoutService.Outcome.TIMED_OUT);
        // Second tick: status is CANCELLATION_PENDING → accept_won metric path (not PAID)
        assertThat(service.processCandidate(orderId))
                .isEqualTo(RestaurantAcceptanceTimeoutService.Outcome.ACCEPT_WON);

        verify(compensationService, times(1)).start(any(), any(), any(), any());
        assertThat(counter("timed_out")).isEqualTo(1.0);
    }

    @Test
    void missingOrderReturnsMissing() {
        UUID missing = UUID.randomUUID();
        when(orderRepository.findById(missing)).thenReturn(Optional.empty());
        Clock clock = Clock.fixed(DEADLINE, ZoneOffset.UTC);

        assertThat(service(clock).processCandidate(missing))
                .isEqualTo(RestaurantAcceptanceTimeoutService.Outcome.MISSING);
        verify(compensationService, never()).start(any(), any(), any(), any());
    }

    private RestaurantAcceptanceTimeoutService service(Clock clock) {
        // Real TransactionTemplate against a no-op manager that runs callbacks inline
        PlatformTransactionManager inline = new PlatformTransactionManager() {
            @Override
            public org.springframework.transaction.TransactionStatus getTransaction(
                    org.springframework.transaction.TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(org.springframework.transaction.TransactionStatus status) {
            }

            @Override
            public void rollback(org.springframework.transaction.TransactionStatus status) {
            }
        };
        return new RestaurantAcceptanceTimeoutService(
                orderRepository,
                compensationService,
                clock,
                inline,
                provider(meterRegistry));
    }

    private double counter(String outcome) {
        var counter = meterRegistry.find(RestaurantAcceptanceTimeoutService.METRIC)
                .tag("outcome", outcome)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static Order paidOrder(Instant paidAt, Duration window) {
        Order o = new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(120_000));
        o.markPaid(paidAt, window);
        return o;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfAvailable(Supplier<T> defaultSupplier) {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getIfUnique(Supplier<T> defaultSupplier) {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }
}

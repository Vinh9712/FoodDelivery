package com.fooddelivery.order.saga;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.PickupAddressSnapshot;
import com.fooddelivery.order.infrastructure.client.DeliveryServiceClient;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryRequest;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryResponse;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryStatusResponse;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDeliveryReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
    private static final int ATTENTION_ATTEMPTS = 8;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private DeliveryServiceClient deliveryClient;
    @Mock
    private OrderCompensationService compensationService;

    private OrderDeliveryReconciliationService service;
    private Order order;
    private UUID orderId;
    private UUID deliveryId;
    private UUID driverId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new OrderDeliveryReconciliationService(
                orderRepository,
                outboxEventRepository,
                deliveryClient,
                compensationService,
                clock,
                emptyMeterRegistry(),
                INITIAL_BACKOFF,
                MAX_BACKOFF,
                ATTENTION_ATTEMPTS);

        order = readyOrderDue();
        orderId = order.getId();
        deliveryId = UUID.randomUUID();
        driverId = UUID.randomUUID();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(outboxEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void lostPostResponseFindsExistingDeliveryAndDoesNotPostOrRefund() {
        when(deliveryClient.findByOrderId(orderId))
                .thenReturn(status(deliveryId, "FINDING_DRIVER", null));

        service.reconcile(orderId);

        verify(deliveryClient, never()).schedule(anyString(), any());
        verifyNoInteractions(compensationService);
        assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(order.getNextDeliveryScheduleAttemptAt()).isNull();
        assertThat(order.getDeliveryScheduleAttempts()).isZero();
    }

    @Test
    void confirmed404RetriesIdempotentPost() {
        when(deliveryClient.findByOrderId(orderId)).thenThrow(notFound());
        when(deliveryClient.schedule(eq("delivery-schedule:" + orderId), any(DeliveryRequest.class)))
                .thenReturn(new DeliveryResponse(deliveryId, orderId, "PENDING", null, null));

        service.reconcile(orderId);

        verify(deliveryClient).schedule(eq("delivery-schedule:" + orderId), any(DeliveryRequest.class));
        assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        verifyNoInteractions(compensationService);
    }

    @Test
    void timeoutOnLookupKeepsReadyAndSchedulesBackoff() {
        when(deliveryClient.findByOrderId(orderId))
                .thenThrow(new RuntimeException("connection timeout"));

        service.reconcile(orderId);

        verify(deliveryClient, never()).schedule(anyString(), any());
        verifyNoInteractions(compensationService);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(order.getDeliveryScheduleAttempts()).isEqualTo(1);
        assertThat(order.getNextDeliveryScheduleAttemptAt()).isEqualTo(NOW.plus(INITIAL_BACKOFF));
        assertThat(order.getLastDeliveryScheduleError()).contains("timeout");
    }

    @Test
    void pendingFindingDriverAndAssignedAttachDeliveryIdWithoutDuplicatePost() {
        when(deliveryClient.findByOrderId(orderId))
                .thenReturn(status(deliveryId, "DRIVER_ASSIGNED", driverId));

        service.reconcile(orderId);

        verify(deliveryClient, never()).schedule(anyString(), any());
        assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(order.getDriverId()).isEqualTo(driverId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
    }

    @Test
    void pickedUpCatchesUpLifecycleUsingSourceTimestamp() {
        Instant pickedUpAt = Instant.parse("2026-07-22T11:30:00Z");
        when(deliveryClient.findByOrderId(orderId)).thenReturn(new DeliveryStatusResponse(
                deliveryId, orderId, "PICKED_UP", driverId, null,
                pickedUpAt, null, null, null, null));

        service.reconcile(orderId);

        assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PICKED_UP);
        // Outbox events are persisted and cleared from the aggregate in the same transaction
        verify(outboxEventRepository).saveAll(any());
    }

    @Test
    void deliveringCatchesUpThroughPickedUpThenDelivering() {
        Instant pickedUpAt = Instant.parse("2026-07-22T11:20:00Z");
        Instant startedAt = Instant.parse("2026-07-22T11:40:00Z");
        when(deliveryClient.findByOrderId(orderId)).thenReturn(new DeliveryStatusResponse(
                deliveryId, orderId, "DELIVERING", driverId, null,
                pickedUpAt, startedAt, null, null, null));

        service.reconcile(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERING);
    }

    @Test
    void deliveredCatchesUpAllAllowedTransitions() {
        Instant pickedUpAt = Instant.parse("2026-07-22T11:10:00Z");
        Instant startedAt = Instant.parse("2026-07-22T11:20:00Z");
        Instant deliveredAt = Instant.parse("2026-07-22T11:50:00Z");
        when(deliveryClient.findByOrderId(orderId)).thenReturn(new DeliveryStatusResponse(
                deliveryId, orderId, "DELIVERED", driverId, null,
                pickedUpAt, startedAt, deliveredAt, null, null));

        service.reconcile(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(compensationService);
    }

    @Test
    void explicitFailedEntersCompensationOnce() {
        when(deliveryClient.findByOrderId(orderId)).thenReturn(new DeliveryStatusResponse(
                deliveryId, orderId, "FAILED", null, "no courier",
                null, null, null, Instant.parse("2026-07-22T11:55:00Z"), "no courier"));

        service.reconcile(orderId);

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(compensationService).start(
                eq(orderId),
                eq(com.fooddelivery.order.domain.model.valueobject.CancellationCode.DELIVERY_FAILED),
                reasonCaptor.capture(),
                eq(OrderEventPayloads.Source.DELIVERY_RECONCILIATION));
        assertThat(reasonCaptor.getValue()).contains("no courier");
        assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
        verify(deliveryClient, never()).schedule(anyString(), any());
    }

    @Test
    void explicitCancelledEntersCompensationOnce() {
        when(deliveryClient.findByOrderId(orderId)).thenReturn(new DeliveryStatusResponse(
                deliveryId, orderId, "CANCELLED", null, "order cancelled",
                null, null, null, null, "order cancelled"));

        service.reconcile(orderId);

        verify(compensationService).start(
                eq(orderId),
                eq(com.fooddelivery.order.domain.model.valueobject.CancellationCode.DELIVERY_FAILED),
                anyString(),
                eq(OrderEventPayloads.Source.DELIVERY_RECONCILIATION));
    }

    @Test
    void highAttemptCountDoesNotRefund() {
        for (int i = 0; i < ATTENTION_ATTEMPTS; i++) {
            order.recordDeliveryScheduleFailure("timeout", NOW.plus(Duration.ofSeconds(30L * (1L << Math.min(i, 3)))), NOW);
        }
        when(deliveryClient.findByOrderId(orderId))
                .thenThrow(new RuntimeException("still unavailable"));

        service.reconcile(orderId);

        verifyNoInteractions(compensationService);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(order.getDeliveryScheduleAttempts()).isGreaterThanOrEqualTo(ATTENTION_ATTEMPTS);
    }

    private DeliveryStatusResponse status(UUID id, String status, UUID driver) {
        return new DeliveryStatusResponse(id, orderId, status, driver, null,
                null, null, null, null, null);
    }

    private FeignException.NotFound notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/internal/v1/deliveries/orders/" + orderId,
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
        return new FeignException.NotFound("not found", request, null, Collections.emptyMap());
    }

    private Order readyOrderDue() {
        UUID customerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        PickupAddressSnapshot pickup = new PickupAddressSnapshot(
                restaurantId, "Pho 24", "0901000000", "12 Le Loi", null, null);
        Order o = Order.create(customerId, restaurantId, "1 Nguyen Hue",
                BigDecimal.valueOf(15000), BigDecimal.ZERO, "req-" + UUID.randomUUID(), pickup);
        o.addItem(UUID.randomUUID(), "Pho", "large", BigDecimal.valueOf(50000), 1);
        o.markPaid(Instant.parse("2026-07-22T10:00:00Z"), Duration.ofMinutes(10));
        o.acceptByRestaurant(UUID.randomUUID());
        o.startPreparing(UUID.randomUUID());
        o.markReadyForPickup(UUID.randomUUID());
        o.clearPendingOutboxEvents();
        // next attempt null => immediately due
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

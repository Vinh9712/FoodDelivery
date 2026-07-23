package com.fooddelivery.order.application;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PickupAddressSnapshot;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.order.saga.OrderCompensationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantOrderServiceTest {

    private final UUID ownerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private OrderCompensationService compensationService;

    private RestaurantOrderService service;

    @BeforeEach
    void setUp() {
        service = new RestaurantOrderService(orderRepository, outboxEventRepository, events, compensationService);
        lenient().doAnswer(inv -> {
            UUID orderId = inv.getArgument(0);
            CancellationCode code = inv.getArgument(1);
            String reason = inv.getArgument(2);
            OrderEventPayloads.Source source = inv.getArgument(3);
            Order order = orderRepository.findById(orderId).orElseThrow();
            order.beginCompensation(reason, code, source, Instant.parse("2026-07-22T12:00:00Z"));
            if (!order.getPendingOutboxEvents().isEmpty()) {
                outboxEventRepository.saveAll(order.getPendingOutboxEvents());
                order.clearPendingOutboxEvents();
            }
            orderRepository.save(order);
            return null;
        }).when(compensationService).start(any(), any(), any(), any());
    }

    @Test
    void acceptMovesPaidToConfirmedAndPersistsOutbox() {
        Order order = paidOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = service.accept(order.getId(), ownerId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(outboxEventRepository).saveAll(any());
        verify(orderRepository).save(order);
    }

    @Test
    void startPreparingMovesConfirmedToPreparing() {
        Order order = confirmedOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = service.startPreparing(order.getId(), ownerId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PREPARING);
        verify(outboxEventRepository).saveAll(any());
    }

    @Test
    void readyPublishesOneApplicationEventAfterStateChange() {
        Order order = preparingOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = service.markReady(order.getId(), ownerId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(RestaurantOrderService.OrderReadyForPickup.class);
        RestaurantOrderService.OrderReadyForPickup ready =
                (RestaurantOrderService.OrderReadyForPickup) eventCaptor.getValue();
        assertThat(ready.orderId()).isEqualTo(order.getId());
        assertThat(ready.customerId()).isEqualTo(customerId);
        assertThat(ready.restaurantId()).isEqualTo(restaurantId);
        assertThat(ready.pickup()).isNotNull();
        assertThat(ready.dropoff()).isNotNull();
        verify(outboxEventRepository).saveAll(any());
    }

    @Test
    void repeatedReadyReturnsCurrentOrderWithoutPublishingAgain() {
        Order order = readyOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        service.markReady(order.getId(), ownerId);

        verifyNoInteractions(events);
        verify(outboxEventRepository, never()).saveAll(any());
    }

    @Test
    void repeatedAcceptIsIdempotentWithoutSecondOutboxBatch() {
        Order order = confirmedOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = service.accept(order.getId(), ownerId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(outboxEventRepository, never()).saveAll(any());
        verifyNoInteractions(events);
    }

    @Test
    void rejectFromPaidMovesToCancellationPending() {
        Order order = paidOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = service.reject(order.getId(), ownerId, "Kitchen capacity exceeded");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        assertThat(result.getRefundStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(result.getCancellationReason()).isEqualTo("Kitchen capacity exceeded");
        verify(compensationService).start(
                eq(order.getId()),
                eq(CancellationCode.RESTAURANT_REJECTED),
                eq("Kitchen capacity exceeded"),
                eq(OrderEventPayloads.Source.RESTAURANT));
        verify(outboxEventRepository).saveAll(any());
        verifyNoInteractions(events);
    }

    @Test
    void rejectFromConfirmedMovesToCancellationPending() {
        Order order = confirmedOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = service.reject(order.getId(), ownerId, "Closed early");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        verify(compensationService).start(
                eq(order.getId()),
                eq(CancellationCode.RESTAURANT_REJECTED),
                eq("Closed early"),
                eq(OrderEventPayloads.Source.RESTAURANT));
        verify(outboxEventRepository).saveAll(any());
    }

    @Test
    void rejectFromPreparingIsConflict() {
        Order order = preparingOrder();
        doThrow(new InvalidOrderStateException("Cannot request cancellation"))
                .when(compensationService).start(any(), any(), any(), any());

        assertThatThrownBy(() -> service.reject(order.getId(), ownerId, "too late"))
                .isInstanceOf(InvalidOrderStateException.class);
        verify(compensationService).start(
                eq(order.getId()),
                eq(CancellationCode.RESTAURANT_REJECTED),
                eq("too late"),
                eq(OrderEventPayloads.Source.RESTAURANT));
        verifyNoInteractions(events);
    }

    @Test
    void skipAndReverseTransitionsAreConflict() {
        Order order = paidOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.startPreparing(order.getId(), ownerId))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThatThrownBy(() -> service.markReady(order.getId(), ownerId))
                .isInstanceOf(InvalidOrderStateException.class);

        Order preparing = preparingOrder();
        when(orderRepository.findById(preparing.getId())).thenReturn(Optional.of(preparing));
        assertThatThrownBy(() -> service.accept(preparing.getId(), ownerId))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void missingOrderThrowsNotFound() {
        UUID missing = UUID.randomUUID();
        when(orderRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(missing, ownerId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listDelegatesToRepositoryByRestaurantAndOptionalStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Order order = paidOrder();
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);
        when(orderRepository.findByRestaurantId(restaurantId, pageable)).thenReturn(page);
        when(orderRepository.findByRestaurantIdAndStatus(restaurantId, OrderStatus.PAID, pageable))
                .thenReturn(page);

        assertThat(service.list(restaurantId, null, pageable).getContent()).containsExactly(order);
        assertThat(service.list(restaurantId, OrderStatus.PAID, pageable).getContent()).containsExactly(order);

        verify(orderRepository).findByRestaurantId(restaurantId, pageable);
        verify(orderRepository).findByRestaurantIdAndStatus(restaurantId, OrderStatus.PAID, pageable);
    }

    @Test
    void readyEventCapturesPickupAndDropoffSnapshots() {
        Order order = preparingOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        service.markReady(order.getId(), ownerId);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        RestaurantOrderService.OrderReadyForPickup event =
                (RestaurantOrderService.OrderReadyForPickup) captor.getValue();
        assertThat(event.pickup()).isEqualTo(order.getPickupAddressSnapshot());
        assertThat(event.dropoff()).isEqualTo(order.getDeliveryAddressSnapshot());
    }

    private Order paidOrder() {
        PickupAddressSnapshot pickup = new PickupAddressSnapshot(
                restaurantId, "Pho 24", "0901000000", "12 Le Loi", null, null);
        Order order = Order.create(customerId, restaurantId, "1 Nguyen Hue",
                BigDecimal.ZERO, BigDecimal.ZERO, "req-" + UUID.randomUUID(), pickup);
        order.clearPendingOutboxEvents();
        order.markPaid(Instant.parse("2026-07-22T00:00:00Z"), Duration.ofMinutes(10));
        order.clearPendingOutboxEvents();
        return order;
    }

    private Order confirmedOrder() {
        Order order = paidOrder();
        order.acceptByRestaurant(ownerId);
        order.clearPendingOutboxEvents();
        return order;
    }

    private Order preparingOrder() {
        Order order = confirmedOrder();
        order.startPreparing(ownerId);
        order.clearPendingOutboxEvents();
        return order;
    }

    private Order readyOrder() {
        Order order = preparingOrder();
        order.markReadyForPickup(ownerId);
        order.clearPendingOutboxEvents();
        return order;
    }
}

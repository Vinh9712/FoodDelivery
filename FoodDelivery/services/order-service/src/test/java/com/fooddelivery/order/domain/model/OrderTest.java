package com.fooddelivery.order.domain.model;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.model.valueobject.AssignedDriverInfo;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.PickupAddressSnapshot;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import com.fooddelivery.order.domain.model.valueobject.VehicleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void followsPaidRestaurantKitchenAndDeliveryHappyPath() {
        Instant paidAt = Instant.parse("2026-07-22T00:00:00Z");
        Order order = pendingOrder();

        order.markPaid(paidAt, Duration.ofMinutes(10));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getRestaurantResponseDeadline()).isEqualTo(paidAt.plus(Duration.ofMinutes(10)));

        order.acceptByRestaurant(UUID.randomUUID());
        order.startPreparing(UUID.randomUUID());
        order.markReadyForPickup(UUID.randomUUID());
        order.markPickedUp(Instant.parse("2026-07-22T00:20:00Z"), OrderEventPayloads.Source.DELIVERY_EVENT);
        order.markDelivering(Instant.parse("2026-07-22T00:25:00Z"), OrderEventPayloads.Source.DELIVERY_EVENT);
        order.markDelivered(Instant.parse("2026-07-22T00:45:00Z"), OrderEventPayloads.Source.DELIVERY_EVENT);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void restaurantCommandsAreIdempotentButCannotSkipOrReverse() {
        Order order = paidOrder();

        order.acceptByRestaurant(UUID.randomUUID());
        int eventCountAfterAccept = order.getPendingOutboxEvents().size();
        order.acceptByRestaurant(UUID.randomUUID());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPendingOutboxEvents()).hasSize(eventCountAfterAccept);
        assertThatThrownBy(() -> order.markReadyForPickup(UUID.randomUUID()))
                .isInstanceOf(InvalidOrderStateException.class);

        order.startPreparing(UUID.randomUUID());
        assertThatThrownBy(() -> order.acceptByRestaurant(UUID.randomUUID()))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void assigningDriverNeverRegistersAnOrderProducedDriverAssignedEvent() {
        Order order = readyOrder();
        AssignedDriverInfo driver = driverInfo();

        order.assignDriver(driver);
        order.assignDriver(driver);

        assertThat(order.getPendingOutboxEvents())
                .noneMatch(event -> event.getEventType().equals("DriverAssigned"));
        assertThat(order.getAssignedDriver()).contains(driver);
    }

    @Test
    void cancellationEdgesMatchFulfillmentDesign() {
        Order paid = paidOrder();
        paid.requestCancellation("timeout",
                com.fooddelivery.order.domain.model.valueobject.CancellationCode.RESTAURANT_ACCEPTANCE_TIMEOUT,
                OrderEventPayloads.Source.SYSTEM_TIMEOUT);
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);

        Order confirmed = paidOrder();
        confirmed.acceptByRestaurant(UUID.randomUUID());
        assertThatThrownBy(() -> confirmed.requestCancellation("timeout",
                com.fooddelivery.order.domain.model.valueobject.CancellationCode.RESTAURANT_ACCEPTANCE_TIMEOUT,
                OrderEventPayloads.Source.SYSTEM_TIMEOUT))
                .isInstanceOf(InvalidOrderStateException.class);

        Order preparing = paidOrder();
        preparing.acceptByRestaurant(UUID.randomUUID());
        preparing.startPreparing(UUID.randomUUID());
        assertThatThrownBy(() -> preparing.requestCancellation("reject",
                com.fooddelivery.order.domain.model.valueobject.CancellationCode.RESTAURANT_REJECTED,
                OrderEventPayloads.Source.RESTAURANT))
                .isInstanceOf(InvalidOrderStateException.class);

        Order ready = readyOrder();
        ready.requestCancellation("no driver",
                com.fooddelivery.order.domain.model.valueobject.CancellationCode.DELIVERY_FAILED,
                OrderEventPayloads.Source.DELIVERY_EVENT);
        assertThat(ready.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);

        Order pickedUp = readyOrder();
        pickedUp.markPickedUp(Instant.parse("2026-07-22T00:20:00Z"), OrderEventPayloads.Source.DELIVERY_EVENT);
        pickedUp.requestCancellation("driver cancelled",
                com.fooddelivery.order.domain.model.valueobject.CancellationCode.DELIVERY_FAILED,
                OrderEventPayloads.Source.DELIVERY_RECONCILIATION);
        assertThat(pickedUp.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
    }

    @Test
    void customerAndAdminCancellationEdges() {
        Order unpaid = pendingOrder();
        unpaid.cancelUnpaid("changed mind",
                com.fooddelivery.order.domain.model.valueobject.CancellationCode.CUSTOMER_REQUESTED,
                OrderEventPayloads.Source.CUSTOMER,
                Instant.parse("2026-07-22T00:01:00Z"));
        assertThat(unpaid.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(unpaid.getCancellationCode())
                .isEqualTo(com.fooddelivery.order.domain.model.valueobject.CancellationCode.CUSTOMER_REQUESTED);

        Order paid = paidOrder();
        paid.requestCancellation("no longer needed",
                com.fooddelivery.order.domain.model.valueobject.CancellationCode.CUSTOMER_REQUESTED,
                OrderEventPayloads.Source.CUSTOMER);
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);

        Order preparing = paidOrder();
        preparing.acceptByRestaurant(UUID.randomUUID());
        preparing.startPreparing(UUID.randomUUID());
        assertThatThrownBy(() -> preparing.requestCancellation("too late",
                com.fooddelivery.order.domain.model.valueobject.CancellationCode.CUSTOMER_REQUESTED,
                OrderEventPayloads.Source.CUSTOMER))
                .isInstanceOf(InvalidOrderStateException.class);

        Order adminPreparing = paidOrder();
        adminPreparing.acceptByRestaurant(UUID.randomUUID());
        adminPreparing.startPreparing(UUID.randomUUID());
        adminPreparing.requestCancellation("ops cancel",
                com.fooddelivery.order.domain.model.valueobject.CancellationCode.ADMIN_CANCELLED,
                OrderEventPayloads.Source.ADMIN);
        assertThat(adminPreparing.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
    }

    @Test
    void paymentFailureCancelsOnlyPendingOrdersWithoutRefund() {
        Order order = pendingOrder();
        Instant failedAt = Instant.parse("2026-07-22T00:01:00Z");

        order.markPaymentFailed("declined", failedAt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getRefundStatus()).isEqualTo(RefundStatus.NOT_REQUIRED);
        assertThat(order.getPendingOutboxEvents())
                .filteredOn(event -> event.getEventType().equals("OrderStatusChanged"))
                .hasSize(1);
        assertThatThrownBy(() -> order.markPaymentFailed("declined", failedAt))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void cancellationRequestsRequireValidReasonAndKeepCapturedPaymentPaid() {
        Order order = paidOrder();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> order.requestCancellation("   ",
                        com.fooddelivery.order.domain.model.valueobject.CancellationCode.RESTAURANT_REJECTED,
                        OrderEventPayloads.Source.RESTAURANT));

        order.requestCancellation(" restaurant declined ",
                com.fooddelivery.order.domain.model.valueobject.CancellationCode.RESTAURANT_REJECTED,
                OrderEventPayloads.Source.RESTAURANT);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getRefundStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(order.getCancellationReason()).isEqualTo("restaurant declined");
        int eventCount = order.getPendingOutboxEvents().size();
        order.requestCancellation("again",
                com.fooddelivery.order.domain.model.valueobject.CancellationCode.RESTAURANT_REJECTED,
                OrderEventPayloads.Source.RESTAURANT);
        assertThat(order.getPendingOutboxEvents()).hasSize(eventCount);
    }

    @Test
    void deliveryTransitionsRejectWrongSourceAndOutboxSequencesArePositiveAndMonotonic() {
        Order order = readyOrder();

        assertThatThrownBy(() -> order.markPickedUp(Instant.parse("2026-07-22T00:20:00Z"),
                OrderEventPayloads.Source.RESTAURANT))
                .isInstanceOf(IllegalArgumentException.class);

        order.markPickedUp(Instant.parse("2026-07-22T00:20:00Z"), OrderEventPayloads.Source.DELIVERY_EVENT);
        order.markDelivering(Instant.parse("2026-07-22T00:25:00Z"), OrderEventPayloads.Source.DELIVERY_EVENT);

        assertThat(order.getPendingOutboxEvents())
                .extracting(OutboxEvent::getAggregateSequence)
                .isSorted()
                .allMatch(sequence -> sequence > 0);
        assertThat(order.getPendingOutboxEvents())
                .allMatch(event -> event.getPartitionKey().equals(order.getId().toString())
                        && event.getEventVersion() == 1);
    }

    @Test
    void preservesValidatedPickupSnapshotForTheOrderLifetime() {
        PickupAddressSnapshot pickup = new PickupAddressSnapshot(
                UUID.randomUUID(), "Pho 24", "0901000000", "12 Le Loi", null, null);

        assertThatIllegalArgumentException().isThrownBy(() -> new PickupAddressSnapshot(
                UUID.randomUUID(), " ", "0901000000", "12 Le Loi", null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> pendingOrder().requestCancellation("x".repeat(501),
                com.fooddelivery.order.domain.model.valueobject.CancellationCode.RESTAURANT_REJECTED,
                OrderEventPayloads.Source.RESTAURANT));

        Order order = Order.create(UUID.randomUUID(), pickup.restaurantId(), "dropoff",
                BigDecimal.ZERO, BigDecimal.ZERO, "request-1", pickup);

        assertThat(order.getPickupAddressSnapshot()).isEqualTo(pickup);
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), UUID.randomUUID(), "dropoff",
                BigDecimal.ZERO, BigDecimal.ZERO, "request-2", pickup))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Order pendingOrder() {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(150_000));
    }

    private Order paidOrder() {
        Order order = pendingOrder();
        order.markPaid(Instant.parse("2026-07-22T00:00:00Z"), Duration.ofMinutes(10));
        return order;
    }

    private Order readyOrder() {
        Order order = paidOrder();
        order.acceptByRestaurant(UUID.randomUUID());
        order.startPreparing(UUID.randomUUID());
        order.markReadyForPickup(UUID.randomUUID());
        return order;
    }

    private AssignedDriverInfo driverInfo() {
        return new AssignedDriverInfo(
                UUID.randomUUID(), "Nguyen Van A", "0987654321", VehicleType.MOTORBIKE,
                "29A1-12345", BigDecimal.valueOf(4.8), Instant.parse("2026-07-22T00:10:00Z"));
    }
}

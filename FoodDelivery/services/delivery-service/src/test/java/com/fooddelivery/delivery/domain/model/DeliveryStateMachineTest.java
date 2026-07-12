package com.fooddelivery.delivery.domain.model;

import com.fooddelivery.delivery.domain.exception.InvalidDeliveryStateException;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryStateMachineTest {

    @Test
    void happyPathTransitions() {
        Delivery delivery = new Delivery(UUID.randomUUID());
        delivery.startFindingDriver();
        delivery.assignDriver(UUID.randomUUID());
        delivery.pickUp();
        delivery.startDelivering();
        delivery.complete();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void cannotSkipFromAssignedToDelivering() {
        Delivery delivery = new Delivery(UUID.randomUUID());
        delivery.assignDriver(UUID.randomUUID());
        assertThatThrownBy(delivery::startDelivering)
                .isInstanceOf(InvalidDeliveryStateException.class);
    }

    @Test
    void cannotCompleteWithoutDelivering() {
        Delivery delivery = new Delivery(UUID.randomUUID());
        delivery.assignDriver(UUID.randomUUID());
        delivery.pickUp();
        assertThatThrownBy(delivery::complete)
                .isInstanceOf(InvalidDeliveryStateException.class);
    }

    @Test
    void cancelFromFindingDriver() {
        Delivery delivery = new Delivery(UUID.randomUUID());
        delivery.startFindingDriver();
        delivery.cancel("customer cancelled");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
    }

    @Test
    void cannotFailAlreadyFailedDelivery() {
        Delivery delivery = new Delivery(UUID.randomUUID());
        UUID driverId = UUID.randomUUID();
        delivery.assignDriver(driverId);
        delivery.fail("first failure");

        assertThatThrownBy(() -> delivery.fail("retry"))
                .isInstanceOf(InvalidDeliveryStateException.class);
        assertThat(delivery.getDriverId()).isEqualTo(driverId);
        assertThat(delivery.getFailureReason()).isEqualTo("first failure");
    }
}

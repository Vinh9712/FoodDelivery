package com.fooddelivery.order.application;

import com.fooddelivery.order.domain.model.valueobject.DeliveryAddressSnapshot;
import com.fooddelivery.order.domain.model.valueobject.PickupAddressSnapshot;
import com.fooddelivery.order.infrastructure.client.DeliveryServiceClient;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReadyDeliverySchedulingCoordinatorTest {

    @Mock
    private DeliveryServiceClient client;

    private ReadyDeliverySchedulingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new ReadyDeliverySchedulingCoordinator(client);
    }

    @Test
    void schedulesOnlyReadyEventWithStableIdempotencyKey() {
        var event = readyEvent();

        coordinator.onReady(event);

        verify(client).schedule(eq("delivery-schedule:" + event.orderId()), argThat(request ->
                request.orderId().equals(event.orderId())
                        && request.customerId().equals(event.customerId())
                        && request.restaurantId().equals(event.restaurantId())
                        && request.pickupAddressSnapshot().restaurantId().equals(event.restaurantId())
                        && request.dropoffAddressSnapshot().addressLine()
                        .equals(event.dropoff().addressLine())));
    }

    @Test
    void networkFailureDoesNotMutateOrCancelOrder() {
        var event = readyEvent();
        doThrow(new RuntimeException("timeout")).when(client).schedule(anyString(), any(DeliveryRequest.class));

        assertThatCode(() -> coordinator.onReady(event)).doesNotThrowAnyException();
        verify(client).schedule(eq("delivery-schedule:" + event.orderId()), any(DeliveryRequest.class));
    }

    private RestaurantOrderService.OrderReadyForPickup readyEvent() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        PickupAddressSnapshot pickup = new PickupAddressSnapshot(
                restaurantId, "Pho 24", "0901000000", "12 Le Loi", null, null);
        DeliveryAddressSnapshot dropoff = new DeliveryAddressSnapshot(
                "1 Nguyen Hue", "District 1", "HCM", null, null);
        return new RestaurantOrderService.OrderReadyForPickup(
                orderId, customerId, restaurantId, pickup, dropoff);
    }
}

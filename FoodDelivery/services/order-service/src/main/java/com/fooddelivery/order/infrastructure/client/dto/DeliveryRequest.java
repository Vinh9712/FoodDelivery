package com.fooddelivery.order.infrastructure.client.dto;

import com.fooddelivery.order.application.RestaurantOrderService;
import com.fooddelivery.order.domain.model.valueobject.DeliveryAddressSnapshot;
import com.fooddelivery.order.domain.model.valueobject.PickupAddressSnapshot;

import java.util.UUID;

/** Request body for Delivery Service schedule after READY_FOR_PICKUP. */
public record DeliveryRequest(
        UUID orderId,
        UUID customerId,
        UUID restaurantId,
        PickupAddressSnapshot pickupAddressSnapshot,
        DeliveryAddressSnapshot dropoffAddressSnapshot
) {
    public static DeliveryRequest from(RestaurantOrderService.OrderReadyForPickup event) {
        return new DeliveryRequest(
                event.orderId(),
                event.customerId(),
                event.restaurantId(),
                event.pickup(),
                event.dropoff());
    }
}

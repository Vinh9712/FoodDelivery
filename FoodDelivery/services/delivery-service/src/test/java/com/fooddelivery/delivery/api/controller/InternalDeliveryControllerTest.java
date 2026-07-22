package com.fooddelivery.delivery.api.controller;

import com.fooddelivery.delivery.api.dto.DeliveryRequest;
import com.fooddelivery.delivery.application.service.DeliveryAssignmentService;
import com.fooddelivery.delivery.domain.exception.DeliveryNotFoundException;
import com.fooddelivery.delivery.domain.exception.DeliveryScheduleConflictException;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.valueobject.Address;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalDeliveryControllerTest {

    private final DeliveryAssignmentService service = mock(DeliveryAssignmentService.class);
    private final InternalDeliveryController controller = new InternalDeliveryController(service);

    @Test
    void noDriverResponsePreservesFindingDriverStatus() {
        UUID orderId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        DeliveryRequest request = sampleRequest(orderId);
        String key = "delivery-schedule:" + orderId;

        when(service.scheduleDelivery(eq(key), any(DeliveryRequest.class))).thenReturn(
                new DeliveryAssignmentService.AssignmentResult(
                        orderId, deliveryId, DeliveryStatus.FINDING_DRIVER, null, false,
                        "No available driver"));

        var response = controller.scheduleDelivery(key, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().deliveryId()).isEqualTo(deliveryId);
        assertThat(response.getBody().status()).isEqualTo("FINDING_DRIVER");
        verify(service).scheduleDelivery(eq(key), any(DeliveryRequest.class));
    }

    @Test
    void scheduleRequiresMatchingIdempotencyKey() {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.scheduleDelivery("wrong-key", sampleRequest(orderId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void scheduleConflictPropagatesAsServiceException() {
        UUID orderId = UUID.randomUUID();
        String key = "delivery-schedule:" + orderId;
        when(service.scheduleDelivery(eq(key), any(DeliveryRequest.class)))
                .thenThrow(new DeliveryScheduleConflictException(orderId));

        assertThatThrownBy(() -> controller.scheduleDelivery(key, sampleRequest(orderId)))
                .isInstanceOf(DeliveryScheduleConflictException.class);
    }

    @Test
    void findByOrderIdReturnsDelivery() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Delivery delivery = new Delivery(
                orderId, customerId, UUID.randomUUID(),
                new Address("12 Le Loi", null, null),
                new Address("1 Nguyen Hue", null, null),
                null,
                "hash",
                "delivery-schedule:" + orderId);
        when(service.getByOrderId(orderId)).thenReturn(delivery);

        var response = controller.findByOrderId(orderId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().orderId()).isEqualTo(orderId);
        assertThat(response.getBody().deliveryId()).isEqualTo(delivery.getId());
    }

    @Test
    void findByOrderIdMissingThrowsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(service.getByOrderId(orderId)).thenThrow(new DeliveryNotFoundException(orderId));

        assertThatThrownBy(() -> controller.findByOrderId(orderId))
                .isInstanceOf(DeliveryNotFoundException.class);
    }

    private DeliveryRequest sampleRequest(UUID orderId) {
        UUID restaurantId = UUID.randomUUID();
        return new DeliveryRequest(
                orderId,
                UUID.randomUUID(),
                restaurantId,
                new DeliveryRequest.PickupAddressSnapshot(
                        restaurantId, "Pho 24", "0901000000", "12 Le Loi",
                        new BigDecimal("10.770000"), new BigDecimal("106.700000")),
                new DeliveryRequest.DropoffAddressSnapshot(
                        "1 Nguyen Hue", "District 1", "HCM", null, null));
    }
}

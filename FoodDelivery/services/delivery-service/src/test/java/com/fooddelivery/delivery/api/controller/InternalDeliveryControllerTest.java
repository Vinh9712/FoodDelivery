package com.fooddelivery.delivery.api.controller;

import com.fooddelivery.delivery.api.dto.DeliveryRequest;
import com.fooddelivery.delivery.application.service.DeliveryAssignmentService;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalDeliveryControllerTest {

    @Test
    void noDriverResponsePreservesFindingDriverStatus() {
        DeliveryAssignmentService service = mock(DeliveryAssignmentService.class);
        UUID orderId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        String address = "123 Test Street";
        when(service.scheduleDelivery(orderId, null, address)).thenReturn(
                new DeliveryAssignmentService.AssignmentResult(
                        orderId, deliveryId, DeliveryStatus.FINDING_DRIVER, null, false,
                        "No available driver"));

        var response = new InternalDeliveryController(service).scheduleDelivery(
                new DeliveryRequest(orderId, null, address));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("FINDING_DRIVER");
    }
}

package com.fooddelivery.delivery.api.controller;

import com.fooddelivery.delivery.application.service.DeliveryAssignmentService;
import com.fooddelivery.delivery.application.service.DeliveryLifecycleService;
import com.fooddelivery.delivery.domain.exception.DeliveryNotFoundException;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.security.DeliveryAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryControllerTest {

    private DeliveryLifecycleService lifecycle;
    private DeliveryAuthorizationService authorization;
    private DeliveryController controller;

    @BeforeEach
    void setUp() {
        lifecycle = mock(DeliveryLifecycleService.class);
        authorization = mock(DeliveryAuthorizationService.class);
        controller = new DeliveryController(mock(DeliveryAssignmentService.class), lifecycle, authorization);
    }

    @Test
    void getByOrderIdReturnsDeliveryForOwningCustomer() {
        UUID orderId = UUID.randomUUID();
        Delivery delivery = new Delivery(orderId, UUID.randomUUID(), null, null, null);
        Authentication customer = mock(Authentication.class);
        when(lifecycle.getDeliveryByOrderId(orderId)).thenReturn(delivery);
        when(authorization.canReadDelivery(delivery, customer)).thenReturn(true);

        var response = controller.getByOrderId(orderId, customer);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().orderId()).isEqualTo(orderId);
        verify(authorization).canReadDelivery(delivery, customer);
    }

    @Test
    void getByOrderIdHidesDeliveryFromCustomerWhoDoesNotOwnIt() {
        UUID orderId = UUID.randomUUID();
        Delivery delivery = new Delivery(orderId, UUID.randomUUID(), null, null, null);
        Authentication customer = mock(Authentication.class);
        when(lifecycle.getDeliveryByOrderId(orderId)).thenReturn(delivery);
        when(authorization.canReadDelivery(delivery, customer)).thenReturn(false);

        assertThatThrownBy(() -> controller.getByOrderId(orderId, customer))
                .isInstanceOf(DeliveryNotFoundException.class);
    }

    @Test
    void getByOrderIdPropagatesMissingDelivery() {
        UUID orderId = UUID.randomUUID();
        Authentication customer = mock(Authentication.class);
        when(lifecycle.getDeliveryByOrderId(orderId)).thenThrow(new DeliveryNotFoundException(orderId));

        assertThatThrownBy(() -> controller.getByOrderId(orderId, customer))
                .isInstanceOf(DeliveryNotFoundException.class);
    }
}

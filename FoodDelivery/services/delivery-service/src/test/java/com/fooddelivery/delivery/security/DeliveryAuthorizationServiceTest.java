package com.fooddelivery.delivery.security;

import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.Address;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryAuthorizationServiceTest {

    private DeliveryRepository deliveries;
    private DriverRepository drivers;
    private DeliveryAuthorizationService authorization;

    @BeforeEach
    void setUp() {
        deliveries = mock(DeliveryRepository.class);
        drivers = mock(DriverRepository.class);
        authorization = new DeliveryAuthorizationService(deliveries, drivers);
    }

    @Test
    void owningCustomerCanReadByOrderId() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Delivery delivery = new Delivery(orderId, customerId, null, new Address("Address", null, null), null);
        when(deliveries.findByOrderId(orderId)).thenReturn(Optional.of(delivery));

        assertThat(authorization.canReadOrder(orderId, auth(customerId, "CUSTOMER"))).isTrue();
        assertThat(authorization.canReadOrder(orderId, auth(UUID.randomUUID(), "CUSTOMER"))).isFalse();
    }

    @Test
    void assignedDriverCanReadByOrderId() {
        UUID orderId = UUID.randomUUID();
        UUID driverUserId = UUID.randomUUID();
        Driver driver = mock(Driver.class);
        Delivery delivery = new Delivery(orderId);
        UUID driverId = UUID.randomUUID();
        delivery.assignDriver(driverId);
        when(deliveries.findByOrderId(orderId)).thenReturn(Optional.of(delivery));
        when(driver.getId()).thenReturn(driverId);
        when(drivers.findByUserId(driverUserId)).thenReturn(Optional.of(driver));

        assertThat(authorization.canReadOrder(orderId, auth(driverUserId, "DRIVER"))).isTrue();
    }

    @Test
    void adminCanReadBeforeLookupAndMissingDeliveryIsDeniedToCustomer() {
        UUID orderId = UUID.randomUUID();

        assertThat(authorization.canReadOrder(orderId, auth(UUID.randomUUID(), "ADMIN"))).isTrue();
        assertThat(authorization.canReadOrder(orderId, auth(UUID.randomUUID(), "CUSTOMER"))).isFalse();
    }

    private Authentication auth(UUID subject, String role) {
        return new UsernamePasswordAuthenticationToken(
                subject.toString(), "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}

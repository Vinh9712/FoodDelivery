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
    void owningCustomerCanReadLoadedDelivery() {
        UUID customerId = UUID.randomUUID();
        Delivery delivery = new Delivery(UUID.randomUUID(), customerId, null,
                new Address("Address", null, null), null);

        assertThat(authorization.canReadDelivery(delivery, auth(customerId, "CUSTOMER"))).isTrue();
    }

    @Test
    void nonOwningCustomerCannotReadLoadedDelivery() {
        Delivery delivery = new Delivery(UUID.randomUUID(), UUID.randomUUID(), null,
                new Address("Address", null, null), null);

        assertThat(authorization.canReadDelivery(delivery, auth(UUID.randomUUID(), "CUSTOMER"))).isFalse();
    }

    @Test
    void adminCanReadLoadedDelivery() {
        Delivery delivery = new Delivery(UUID.randomUUID());

        assertThat(authorization.canReadDelivery(delivery, auth(UUID.randomUUID(), "ADMIN"))).isTrue();
    }

    @Test
    void serviceCanReadLoadedDelivery() {
        Delivery delivery = new Delivery(UUID.randomUUID());

        assertThat(authorization.canReadDelivery(delivery, auth(UUID.randomUUID(), "SERVICE"))).isTrue();
    }

    @Test
    void assignedDriverCanReadLoadedDelivery() {
        UUID driverUserId = UUID.randomUUID();
        Driver driver = mock(Driver.class);
        Delivery delivery = new Delivery(UUID.randomUUID());
        UUID driverId = UUID.randomUUID();
        delivery.assignDriver(driverId);
        when(driver.getId()).thenReturn(driverId);
        when(drivers.findByUserId(driverUserId)).thenReturn(Optional.of(driver));

        assertThat(authorization.canReadDelivery(delivery, auth(driverUserId, "DRIVER"))).isTrue();
    }

    @Test
    void absentDeliveryCannotBeReadByCustomer() {
        assertThat(authorization.canReadDelivery(null, auth(UUID.randomUUID(), "CUSTOMER"))).isFalse();
    }

    @Test
    void canReadByIdStillLoadsDeliveryFromRepository() {
        UUID deliveryId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Delivery delivery = new Delivery(UUID.randomUUID(), customerId, null,
                new Address("Address", null, null), null);
        when(deliveries.findById(deliveryId)).thenReturn(Optional.of(delivery));

        assertThat(authorization.canRead(deliveryId, auth(customerId, "CUSTOMER"))).isTrue();
    }

    private Authentication auth(UUID subject, String role) {
        return new UsernamePasswordAuthenticationToken(
                subject.toString(), "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}

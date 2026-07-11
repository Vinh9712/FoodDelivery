package com.fooddelivery.order.security;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderAuthorizationServiceTests {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderAuthorizationService authorization = new OrderAuthorizationService(orderRepository);

    @Test
    void customerCanOnlyReadOwnOrder() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(new Order(customerId, UUID.randomUUID(), BigDecimal.TEN)));

        assertThat(authorization.canRead(orderId, authentication(customerId, "CUSTOMER"))).isTrue();
        assertThat(authorization.canRead(orderId, authentication(UUID.randomUUID(), "CUSTOMER"))).isFalse();
    }

    @Test
    void adminCanReadAnyOrderWithoutOwnershipLookup() {
        assertThat(authorization.canRead(UUID.randomUUID(), authentication(UUID.randomUUID(), "ADMIN"))).isTrue();
        verifyNoInteractions(orderRepository);
    }

    private UsernamePasswordAuthenticationToken authentication(UUID subject, String role) {
        return new UsernamePasswordAuthenticationToken(
                subject.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}

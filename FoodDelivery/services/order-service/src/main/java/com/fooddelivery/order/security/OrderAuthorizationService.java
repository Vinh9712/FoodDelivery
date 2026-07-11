package com.fooddelivery.order.security;

import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("orderAuthorization")
@RequiredArgsConstructor
public class OrderAuthorizationService {

    private final OrderRepository orderRepository;

    public boolean canRead(UUID orderId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (hasRole(authentication, "ADMIN")) {
            return true;
        }
        if (!hasRole(authentication, "CUSTOMER")) {
            return false;
        }
        try {
            UUID customerId = UUID.fromString(authentication.getName());
            return orderRepository.findById(orderId)
                    .map(order -> customerId.equals(order.getCustomerId()))
                    .orElse(false);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}

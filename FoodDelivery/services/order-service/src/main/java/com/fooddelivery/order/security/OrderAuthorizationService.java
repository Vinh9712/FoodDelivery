package com.fooddelivery.order.security;

import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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

    /** Same ownership rule as {@link #canRead} — customer owner or admin. */
    public boolean canCancel(UUID orderId, Authentication authentication) {
        return canRead(orderId, authentication);
    }

    /**
     * Resolves the customer filter for {@code GET /api/v1/orders}.
     * <ul>
     *   <li>ADMIN: optional {@code userId} (null = all customers)</li>
     *   <li>CUSTOMER: always own id; {@code userId} if present must match principal</li>
     * </ul>
     *
     * @return customerId to filter by, or null when admin lists the whole system
     */
    public UUID resolveListCustomerFilter(UUID requestedUserId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (hasRole(authentication, "ADMIN")) {
            return requestedUserId;
        }
        if (!hasRole(authentication, "CUSTOMER")) {
            throw new AccessDeniedException("Not allowed to list orders");
        }
        UUID principalId;
        try {
            principalId = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid principal");
        }
        if (requestedUserId != null && !requestedUserId.equals(principalId)) {
            throw new AccessDeniedException("Customers may only list their own orders");
        }
        return principalId;
    }

    public boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}

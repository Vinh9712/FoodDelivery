package com.fooddelivery.delivery.security;

import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("deliveryAuthorization")
@RequiredArgsConstructor
public class DeliveryAuthorizationService {

    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;

    public boolean canRead(UUID deliveryId, Authentication authentication) {
        return canReadDelivery(deliveryRepository.findById(deliveryId).orElse(null), authentication);
    }

    public boolean canReadDelivery(Delivery delivery, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (hasRole(authentication, "ADMIN") || hasRole(authentication, "SERVICE")) {
            return true;
        }
        if (delivery == null) {
            return false;
        }
        UUID subject = subject(authentication);
        if (subject == null) {
            return false;
        }
        if (hasRole(authentication, "CUSTOMER")) {
            return subject.equals(delivery.getCustomerId());
        }
        if (hasRole(authentication, "DRIVER")) {
            return driverRepository.findByUserId(subject)
                    .map(Driver::getId)
                    .map(id -> id.equals(delivery.getDriverId()))
                    .orElse(false);
        }
        return false;
    }

    public boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    private UUID subject(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

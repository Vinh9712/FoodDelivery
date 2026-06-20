package com.fooddelivery.customer.api.dto.response;

import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.model.User;

import java.time.Instant;
import java.util.UUID;

public record UserDetailResponse(
    UUID id,
    String email,
    String phone,
    String role,
    boolean active,
    boolean emailVerified,
    Instant lastLoginAt,
    Instant createdAt,
    String fullName,
    String avatarUrl,
    String customerType,
    Integer loyaltyPoints
) {
    public static UserDetailResponse from(User user, Customer customer) {
        return new UserDetailResponse(
            user.getId(),
            user.getEmail(),
            user.getPhone(),
            user.getRole().name(),
            user.isActive(),
            user.isEmailVerified(),
            user.getLastLoginAt(),
            user.getCreatedAt(),
            customer != null ? customer.getFullName() : null,
            customer != null ? customer.getAvatarUrl() : null,
            customer != null ? customer.getCustomerType().name() : null,
            customer != null ? customer.getLoyaltyPoints() : null
        );
    }
}

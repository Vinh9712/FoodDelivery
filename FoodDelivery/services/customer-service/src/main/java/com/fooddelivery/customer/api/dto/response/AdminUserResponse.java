package com.fooddelivery.customer.api.dto.response;

import com.fooddelivery.customer.domain.model.User;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
    UUID id,
    String email,
    String phone,
    String role,
    boolean active,
    boolean emailVerified,
    Instant lastLoginAt,
    Instant createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
            user.getId(),
            user.getEmail(),
            user.getPhone(),
            user.getRole().name(),
            user.isActive(),
            user.isEmailVerified(),
            user.getLastLoginAt(),
            user.getCreatedAt()
        );
    }
}

package com.fooddelivery.authentication.api.dto.response;

import com.fooddelivery.authentication.domain.model.User;

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
    Instant createdAt
) {
    public static UserDetailResponse from(User user) {
        return new UserDetailResponse(
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

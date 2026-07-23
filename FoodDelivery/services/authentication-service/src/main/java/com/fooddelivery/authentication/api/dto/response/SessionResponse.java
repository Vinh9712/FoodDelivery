package com.fooddelivery.authentication.api.dto.response;

import com.fooddelivery.authentication.domain.model.UserSession;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
    UUID id,
    String deviceName,
    String deviceType,
    String browser,
    String os,
    String ipAddress,
    boolean isCurrent,
    Instant lastUsedAt,
    Instant createdAt
) {
    public static SessionResponse from(UserSession session) {
        return new SessionResponse(
            session.getId(),
            session.getDeviceName(),
            session.getDeviceType(),
            session.getBrowser(),
            session.getOs(),
            session.getIpAddress(),
            session.isCurrent(),
            session.getLastUsedAt(),
            session.getCreatedAt()
        );
    }
}

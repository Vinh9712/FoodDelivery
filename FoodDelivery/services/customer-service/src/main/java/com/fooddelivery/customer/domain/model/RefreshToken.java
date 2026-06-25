package com.fooddelivery.customer.domain.model;

import com.fooddelivery.commonweb.base.BaseEntity;
import com.fooddelivery.customer.domain.model.enums.RefreshTokenStatus;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "refresh_tokens")
@SQLRestriction("is_deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RefreshTokenStatus status;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "session_id")
    private UUID sessionId;

    @PrePersist
    private void ensureId() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
    }
    public static RefreshToken issue(User user, String tokenHash, Instant expiresAt,
                                     String deviceInfo, String ipAddress) {
        return issue(user, tokenHash, expiresAt, deviceInfo, ipAddress, null);
    }

    public static RefreshToken issue(User user, String tokenHash, Instant expiresAt,
                                     String deviceInfo, String ipAddress, UUID sessionId) {
        if (user == null || isBlank(tokenHash) || expiresAt == null) {
            throw new IllegalArgumentException("user, tokenHash and expiresAt are required");
        }
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.user = user;
        refreshToken.tokenHash = tokenHash;
        refreshToken.expiresAt = expiresAt;
        refreshToken.status = RefreshTokenStatus.ACTIVE;
        refreshToken.deviceInfo = deviceInfo;
        refreshToken.ipAddress = ipAddress;
        refreshToken.sessionId = sessionId;
        return refreshToken;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return status == RefreshTokenStatus.ACTIVE && !isExpired();
    }

    public void revoke() {
        this.status = RefreshTokenStatus.REVOKED;
        this.revokedAt = Instant.now();
    }

    public void markExpired() {
        this.status = RefreshTokenStatus.EXPIRED;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

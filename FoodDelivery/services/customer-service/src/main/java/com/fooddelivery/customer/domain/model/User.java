package com.fooddelivery.customer.domain.model;

import com.fooddelivery.customer.domain.model.valueobject.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.fooddelivery.customer.domain.util.UuidCreator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private List<RefreshToken> refreshTokens = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User(UUID id, Email email, PasswordHash passwordHash, Role role) {
        this.id = id;
        this.email = email.value();
        this.passwordHash = passwordHash.value();
        this.role = role;
        this.isActive = true;
        this.emailVerified = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static User create(Email email, PasswordHash passwordHash, Role role) {
        return new User(UuidCreator.nextUuidV7(), email, passwordHash, role);
    }

    public Email getEmail() {
        return new Email(this.email);
    }

    public PasswordHash getPasswordHash() {
        return new PasswordHash(this.passwordHash);
    }

    public boolean authenticate(String rawPassword) {
        if (!isActive) return false;
        boolean ok = getPasswordHash().matches(rawPassword);
        if (ok) {
            this.lastLoginAt = Instant.now();
            this.updatedAt = this.lastLoginAt;
        }
        return ok;
    }

    public void deactivate() {
        this.isActive = false;
        revokeAllTokens();
        this.updatedAt = Instant.now();
    }

    public void verifyEmail() {
        this.emailVerified = true;
        this.updatedAt = Instant.now();
    }

    public RefreshToken issueRefreshToken(TokenHash tokenHash, DeviceInfo deviceInfo, IpAddress ipAddress, Instant expiresAt) {
        RefreshToken token = RefreshToken.create(this.id, tokenHash.value(), expiresAt, deviceInfo.value(), ipAddress.value());
        refreshTokens.add(token);
        this.updatedAt = Instant.now();
        return token;
    }

    public void revokeRefreshToken(UUID tokenId) {
        RefreshToken token = refreshTokens.stream()
                .filter(t -> t.getId().equals(tokenId))
                .findFirst()
                .orElseThrow(() -> new com.fooddelivery.customer.domain.exception.RefreshTokenNotFoundException(tokenId));
        token.revoke();
        this.updatedAt = Instant.now();
    }

    public void revokeAllTokens() {
        refreshTokens.forEach(RefreshToken::revoke);
        this.updatedAt = Instant.now();
    }
}

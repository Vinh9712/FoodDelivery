package com.fooddelivery.customer.domain.repository;

import com.fooddelivery.customer.domain.model.RefreshToken;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    RefreshToken save(RefreshToken token);
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void revokeAllByUserId(UUID userId);
    void revokeByTokenHash(String tokenHash);
    void revokeAllBySessionId(UUID sessionId);
}

package com.fooddelivery.authentication.domain.repository;

import com.fooddelivery.authentication.domain.model.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void invalidateUnusedByUserId(UUID userId, Instant now);
}

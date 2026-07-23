package com.fooddelivery.authentication.infrastructure.persistence;

import com.fooddelivery.authentication.domain.model.PasswordResetToken;
import com.fooddelivery.authentication.domain.repository.PasswordResetTokenRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJPARepository jpaRepository;

    public PasswordResetTokenRepositoryAdapter(PasswordResetTokenJPARepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return jpaRepository.save(token);
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash);
    }

    @Override
    public void invalidateUnusedByUserId(UUID userId, Instant now) {
        jpaRepository.invalidateUnusedByUserId(userId, now);
    }
}

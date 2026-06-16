package com.fooddelivery.customer.infrastructure.persistence;

import com.fooddelivery.customer.domain.model.RefreshToken;
import com.fooddelivery.customer.domain.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {
    private final RefreshTokenJPARepository refreshTokenJPARepository;

    public RefreshTokenRepositoryAdapter(RefreshTokenJPARepository refreshTokenJPARepository) {
        this.refreshTokenJPARepository = refreshTokenJPARepository;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        return refreshTokenJPARepository.save(token);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return refreshTokenJPARepository.findByTokenHash(tokenHash);
    }

    @Override
    public void revokeAllByUserId(UUID userId) {
        refreshTokenJPARepository.revokeAllByUserId(userId);
    }

    @Override
    public void revokeByTokenHash(String tokenHash) {
        RefreshToken token = refreshTokenJPARepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new com.fooddelivery.commonweb.exception.BusinessRuleException("Refresh token not found"));
        if (token.isActive()) {
            token.revoke();
            refreshTokenJPARepository.save(token);
        }
    }
}

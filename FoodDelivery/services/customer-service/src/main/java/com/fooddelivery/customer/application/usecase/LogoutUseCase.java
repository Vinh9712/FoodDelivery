package com.fooddelivery.customer.application.usecase;

import com.fooddelivery.customer.application.command.LogoutCommand;
import com.fooddelivery.customer.domain.repository.RefreshTokenRepository;
import com.fooddelivery.customer.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutUseCase {

    private final RefreshTokenRepository refreshTokenRepository;

    public LogoutUseCase(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public void execute(LogoutCommand command) {
        String hashedToken = SecurityUtils.hashToken(command.refreshToken());
        refreshTokenRepository.revokeByTokenHash(hashedToken);
    }
}

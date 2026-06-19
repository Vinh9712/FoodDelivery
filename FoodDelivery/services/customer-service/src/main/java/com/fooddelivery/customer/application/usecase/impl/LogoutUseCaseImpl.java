package com.fooddelivery.customer.application.usecase.impl;

import com.fooddelivery.customer.application.command.LogoutCommand;
import com.fooddelivery.customer.application.usecase.LogoutUseCase;
import com.fooddelivery.customer.domain.repository.RefreshTokenRepository;
import com.fooddelivery.customer.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutUseCaseImpl implements LogoutUseCase {

    private final RefreshTokenRepository refreshTokenRepository;

    public LogoutUseCaseImpl(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional
    public void execute(LogoutCommand command) {
        String hashedToken = SecurityUtils.hashToken(command.refreshToken());
        refreshTokenRepository.revokeByTokenHash(hashedToken);
    }
}
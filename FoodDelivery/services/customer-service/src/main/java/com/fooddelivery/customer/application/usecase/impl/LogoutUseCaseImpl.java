package com.fooddelivery.customer.application.usecase.impl;

import com.fooddelivery.customer.application.command.LogoutCommand;
import com.fooddelivery.customer.application.usecase.LogoutUseCase;
import com.fooddelivery.customer.domain.repository.RefreshTokenRepository;
import com.fooddelivery.customer.domain.repository.UserSessionRepository;
import com.fooddelivery.customer.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutUseCaseImpl implements LogoutUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;

    public LogoutUseCaseImpl(RefreshTokenRepository refreshTokenRepository,
                             UserSessionRepository userSessionRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    @Transactional
    public void execute(LogoutCommand command) {
        String hashedToken = SecurityUtils.hashToken(command.refreshToken());
        refreshTokenRepository.revokeByTokenHash(hashedToken);
        // Session is_current will be handled when a new login occurs
    }
}
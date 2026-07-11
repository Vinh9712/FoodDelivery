package com.fooddelivery.authentication.application.usecase.impl;

import com.fooddelivery.authentication.application.command.LogoutCommand;
import com.fooddelivery.authentication.application.usecase.LogoutUseCase;
import com.fooddelivery.authentication.domain.repository.RefreshTokenRepository;
import com.fooddelivery.authentication.domain.repository.UserSessionRepository;
import com.fooddelivery.authentication.utils.SecurityUtils;
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
        refreshTokenRepository.findByTokenHash(hashedToken).ifPresent(token -> {
            refreshTokenRepository.revokeByTokenHash(hashedToken);
            if (token.getSessionId() != null) {
                refreshTokenRepository.revokeAllBySessionId(token.getSessionId());
                userSessionRepository.findById(token.getSessionId()).ifPresent(session -> {
                    session.softDelete();
                    userSessionRepository.save(session);
                });
            }
        });
    }
}

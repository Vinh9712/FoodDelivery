package com.fooddelivery.customer.application.usecase.impl;

import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.customer.application.command.RefreshTokenCommand;
import com.fooddelivery.customer.api.dto.response.AuthResponse;
import com.fooddelivery.customer.application.service.UserAgentParser;
import com.fooddelivery.customer.application.usecase.RefreshTokenUseCase;
import com.fooddelivery.customer.config.JwtTokenProvider;
import com.fooddelivery.customer.domain.model.RefreshToken;
import com.fooddelivery.customer.domain.model.User;
import com.fooddelivery.customer.domain.repository.RefreshTokenRepository;
import com.fooddelivery.customer.domain.repository.UserSessionRepository;
import com.fooddelivery.customer.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class RefreshTokenUseCaseImpl implements RefreshTokenUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserAgentParser userAgentParser;
    private final UserSessionRepository userSessionRepository;

    public RefreshTokenUseCaseImpl(
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider,
            UserAgentParser userAgentParser,
            UserSessionRepository userSessionRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userAgentParser = userAgentParser;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    @Transactional
    public AuthResponse execute(RefreshTokenCommand command) {
        String hashedToken = SecurityUtils.hashToken(command.refreshToken());

        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new BusinessRuleException("Invalid or expired refresh token"));

        if (!oldToken.isActive()) {
            throw new BusinessRuleException("Invalid or expired refresh token");
        }

        oldToken.revoke();
        refreshTokenRepository.save(oldToken);

        User user = oldToken.getUser();
        if (!user.isActive()) {
            throw new BusinessRuleException("User account is deactivated");
        }

        UUID sessionId = oldToken.getSessionId();
        if (sessionId != null) {
            userSessionRepository.findById(sessionId).ifPresent(session -> {
                session.markUsed(command.ipAddress());
                userSessionRepository.save(session);
            });
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(),
                user.getRole().name());
        String rawRefreshToken = SecurityUtils.generateRandomToken();
        String newTokenHash = SecurityUtils.hashToken(rawRefreshToken);
        Instant expiryDate = Instant.now().plus(7, ChronoUnit.DAYS);

        RefreshToken freshToken = RefreshToken.issue(
                user,
                newTokenHash,
                expiryDate,
                command.deviceInfo(),
                command.ipAddress(),
                sessionId);
        refreshTokenRepository.save(freshToken);

        return new AuthResponse(
                newAccessToken,
                rawRefreshToken,
                "Bearer",
                jwtTokenProvider.getExpirationMs());
    }
}
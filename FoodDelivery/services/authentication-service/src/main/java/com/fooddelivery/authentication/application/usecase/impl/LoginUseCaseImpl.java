package com.fooddelivery.authentication.application.usecase.impl;

import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.authentication.application.command.LoginCommand;
import com.fooddelivery.authentication.api.dto.response.AuthResponse;
import com.fooddelivery.authentication.application.service.UserAgentParser;
import com.fooddelivery.authentication.application.usecase.LoginUseCase;
import com.fooddelivery.authentication.config.JwtTokenProvider;
import com.fooddelivery.authentication.domain.model.RefreshToken;
import com.fooddelivery.authentication.domain.model.User;
import com.fooddelivery.authentication.domain.model.UserSession;
import com.fooddelivery.authentication.domain.repository.RefreshTokenRepository;
import com.fooddelivery.authentication.domain.repository.UserRepository;
import com.fooddelivery.authentication.domain.repository.UserSessionRepository;
import com.fooddelivery.authentication.domain.vo.DeviceInfo;
import com.fooddelivery.authentication.utils.SecurityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class LoginUseCaseImpl implements LoginUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserAgentParser userAgentParser;
    private final UserSessionRepository userSessionRepository;

    public LoginUseCaseImpl(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder,
            UserAgentParser userAgentParser,
            UserSessionRepository userSessionRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.userAgentParser = userAgentParser;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    @Transactional
    public AuthResponse execute(LoginCommand command) {
        User user = userRepository.findByEmail(command.email().trim().toLowerCase())
                .orElseThrow(() -> new BusinessRuleException("Incorrect email or password"));

        if (!user.isActive()) {
            throw new BusinessRuleException("User account is deactivated");
        }

        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new BusinessRuleException("Incorrect email or password");
        }

        user.markLoggedIn();
        userRepository.save(user);

        DeviceInfo deviceInfo = userAgentParser.parse(command.deviceInfo());

        userSessionRepository.markNotCurrentByUserId(user.getId());
        UserSession session = UserSession.create(
                user,
                deviceInfo.deviceName(),
                deviceInfo.deviceType(),
                deviceInfo.browser(),
                deviceInfo.os(),
                command.ipAddress());
        session = userSessionRepository.save(session);

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                session.getId());

        String rawRefreshToken = SecurityUtils.generateRandomToken();
        String tokenHash = SecurityUtils.hashToken(rawRefreshToken);
        Instant expiryDate = Instant.now().plus(7, ChronoUnit.DAYS);

        RefreshToken refreshToken = RefreshToken.issue(
                user,
                tokenHash,
                expiryDate,
                command.deviceInfo(),
                command.ipAddress(),
                session.getId());
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtTokenProvider.getExpirationMs());
    }
}

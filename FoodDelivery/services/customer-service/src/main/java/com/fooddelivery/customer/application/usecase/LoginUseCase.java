package com.fooddelivery.customer.application.usecase;

import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.customer.application.command.LoginCommand;
import com.fooddelivery.customer.api.dto.response.AuthResponse;
import com.fooddelivery.customer.config.JwtTokenProvider;
import com.fooddelivery.customer.domain.model.RefreshToken;
import com.fooddelivery.customer.domain.model.User;
import com.fooddelivery.customer.domain.repository.RefreshTokenRepository;
import com.fooddelivery.customer.domain.repository.UserRepository;
import com.fooddelivery.customer.utils.SecurityUtils;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class LoginUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public LoginUseCase(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

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

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());

        String rawRefreshToken = SecurityUtils.generateRandomToken();
        String tokenHash = SecurityUtils.hashToken(rawRefreshToken);
        Instant expiryDate = Instant.now().plus(7, ChronoUnit.DAYS);

        RefreshToken refreshToken = RefreshToken.issue(
                user,
                tokenHash,
                expiryDate,
                command.deviceInfo(),
                command.ipAddress());
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtTokenProvider.getExpirationMs());
    }
}

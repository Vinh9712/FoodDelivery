package com.fooddelivery.authentication.application.usecase.impl;

import com.fooddelivery.authentication.application.command.ChangePasswordCommand;
import com.fooddelivery.authentication.application.command.ForgotPasswordCommand;
import com.fooddelivery.authentication.application.command.ResetPasswordCommand;
import com.fooddelivery.authentication.application.usecase.PasswordUseCase;
import com.fooddelivery.authentication.domain.model.PasswordResetToken;
import com.fooddelivery.authentication.domain.model.User;
import com.fooddelivery.authentication.domain.repository.PasswordResetTokenRepository;
import com.fooddelivery.authentication.domain.repository.RefreshTokenRepository;
import com.fooddelivery.authentication.domain.repository.UserRepository;
import com.fooddelivery.authentication.domain.repository.UserSessionRepository;
import com.fooddelivery.commonweb.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordUseCaseImpl implements PasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(PasswordUseCaseImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration resetTokenTtl;

    public PasswordUseCaseImpl(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserSessionRepository userSessionRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.security.password-reset-ttl:30m}") Duration resetTokenTtl) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.resetTokenTtl = resetTokenTtl != null ? resetTokenTtl : Duration.ofMinutes(30);
    }

    /**
     * Always succeeds with a generic outcome (no email enumeration).
     * In environments without email delivery, the raw token is logged for local testing.
     */
    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordCommand command) {
        String email = normalizeEmail(command.email());
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            log.info("Password reset requested for unknown/inactive email={}", email);
            return;
        }

        User user = userOpt.get();
        Instant now = clock.instant();
        passwordResetTokenRepository.invalidateUnusedByUserId(user.getId(), now);

        String rawToken = generateRawToken();
        PasswordResetToken token = PasswordResetToken.create(
                user, sha256(rawToken), now.plus(resetTokenTtl));
        passwordResetTokenRepository.save(token);

        // No external mailer yet — log for local/dev recovery flows.
        log.info("PASSWORD_RESET token issued userId={} email={} token={} expiresAt={}",
                user.getId(), email, rawToken, token.getExpiresAt());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordCommand command) {
        if (command.token() == null || command.token().isBlank()) {
            throw new BusinessRuleException("Reset token is required");
        }
        Instant now = clock.instant();
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(sha256(command.token().trim()))
                .orElseThrow(() -> new BusinessRuleException("Invalid or expired reset token"));
        if (!token.isUsable(now)) {
            throw new BusinessRuleException("Invalid or expired reset token");
        }

        User user = token.getUser();
        if (!user.isActive()) {
            throw new BusinessRuleException("User account is inactive");
        }

        user.changePassword(passwordEncoder.encode(command.newPassword()));
        userRepository.save(user);
        token.markUsed(now);
        passwordResetTokenRepository.save(token);
        passwordResetTokenRepository.invalidateUnusedByUserId(user.getId(), now);
        revokeAllAuth(user.getId());
        log.info("Password reset completed for userId={}", user.getId());
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessRuleException("User not found"));
        if (!user.isActive()) {
            throw new BusinessRuleException("User account is inactive");
        }
        if (!passwordEncoder.matches(command.oldPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        if (passwordEncoder.matches(command.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("New password must be different from the current password");
        }

        user.changePassword(passwordEncoder.encode(command.newPassword()));
        userRepository.save(user);
        passwordResetTokenRepository.invalidateUnusedByUserId(user.getId(), clock.instant());
        revokeAllAuth(user.getId());
        log.info("Password changed for userId={}", user.getId());
    }

    private void revokeAllAuth(java.util.UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        userSessionRepository.revokeAllByUserId(userId);
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}

package com.fooddelivery.authentication;

import com.fooddelivery.authentication.application.command.ChangePasswordCommand;
import com.fooddelivery.authentication.application.command.ForgotPasswordCommand;
import com.fooddelivery.authentication.application.command.ResetPasswordCommand;
import com.fooddelivery.authentication.application.usecase.impl.PasswordUseCaseImpl;
import com.fooddelivery.authentication.domain.model.PasswordResetToken;
import com.fooddelivery.authentication.domain.model.User;
import com.fooddelivery.authentication.domain.model.enums.UserRole;
import com.fooddelivery.authentication.domain.repository.PasswordResetTokenRepository;
import com.fooddelivery.authentication.domain.repository.RefreshTokenRepository;
import com.fooddelivery.authentication.domain.repository.UserRepository;
import com.fooddelivery.authentication.domain.repository.UserSessionRepository;
import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordUseCaseTests {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    private UserRepository userRepository;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private UserSessionRepository userSessionRepository;
    private PasswordEncoder passwordEncoder;
    private PasswordUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        userSessionRepository = mock(UserSessionRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        useCase = new PasswordUseCaseImpl(
                userRepository,
                passwordResetTokenRepository,
                refreshTokenRepository,
                userSessionRepository,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(30));
    }

    @Test
    void forgotPassword_UnknownEmailIsSilent() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        useCase.forgotPassword(new ForgotPasswordCommand("missing@example.com"));

        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void forgotPassword_IssuesTokenForActiveUser() {
        User user = activeUser("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.forgotPassword(new ForgotPasswordCommand(" User@Example.com "));

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).invalidateUnusedByUserId(eq(user.getId()), eq(NOW));
        verify(passwordResetTokenRepository).save(captor.capture());
        assertEquals(64, captor.getValue().getTokenHash().length());
        assertTrue(captor.getValue().getExpiresAt().isAfter(NOW));
    }

    @Test
    void resetPassword_UpdatesPasswordAndRevokesSessions() {
        User user = activeUser("user@example.com");
        String rawToken = "raw-reset-token";
        String hash = sha256(rawToken);
        PasswordResetToken token = PasswordResetToken.create(user, hash, NOW.plusSeconds(600));
        when(passwordResetTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.resetPassword(new ResetPasswordCommand(rawToken, "NewPass1!"));

        assertEquals("new-hash", user.getPasswordHash());
        assertEquals(NOW, token.getUsedAt());
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
        verify(userSessionRepository).revokeAllByUserId(user.getId());
    }

    @Test
    void resetPassword_RejectsExpiredToken() {
        User user = activeUser("user@example.com");
        String rawToken = "expired";
        PasswordResetToken token = PasswordResetToken.create(user, sha256(rawToken), NOW.minusSeconds(1));
        when(passwordResetTokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));

        assertThrows(BusinessRuleException.class,
                () -> useCase.resetPassword(new ResetPasswordCommand(rawToken, "NewPass1!")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_RequiresCorrectOldPassword() {
        User user = activeUser("user@example.com");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThrows(BusinessRuleException.class, () -> useCase.changePassword(
                new ChangePasswordCommand(user.getId(), "wrong", "NewPass1!")));
    }

    @Test
    void changePassword_SuccessRevokesAuth() {
        User user = activeUser("user@example.com");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass1!", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass1!", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass1!")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.changePassword(new ChangePasswordCommand(user.getId(), "OldPass1!", "NewPass1!"));

        assertEquals("new-hash", user.getPasswordHash());
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
        verify(userSessionRepository).revokeAllByUserId(user.getId());
    }

    private User activeUser(String email) {
        User user = User.register(email, "0901234567", "old-hash", UserRole.CUSTOMER);
        setId(user, UuidCreator.getTimeOrderedEpoch());
        return user;
    }

    private static void setId(User user, UUID id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String sha256(String raw) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

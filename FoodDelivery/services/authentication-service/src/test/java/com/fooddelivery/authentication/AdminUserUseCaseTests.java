package com.fooddelivery.authentication;

import com.fooddelivery.authentication.application.command.CreateAdminUserCommand;
import com.fooddelivery.authentication.application.service.SecurityAuditLogger;
import com.fooddelivery.authentication.application.usecase.impl.AdminUserUseCaseImpl;
import com.fooddelivery.authentication.domain.model.User;
import com.fooddelivery.authentication.domain.model.enums.UserRole;
import com.fooddelivery.authentication.domain.repository.UserRepository;
import com.fooddelivery.authentication.domain.repository.RefreshTokenRepository;
import com.fooddelivery.authentication.domain.repository.UserSessionRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserUseCaseTests {

    @Test
    void createUser_ShouldAuditActingAdmin() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SecurityAuditLogger auditLogger = mock(SecurityAuditLogger.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserSessionRepository userSessionRepository = mock(UserSessionRepository.class);
        AdminUserUseCaseImpl useCase = new AdminUserUseCaseImpl(
                userRepository,
                passwordEncoder,
                auditLogger,
                refreshTokenRepository,
                userSessionRepository);
        UUID actorId = UuidCreator.getTimeOrderedEpoch();
        UUID createdUserId = UuidCreator.getTimeOrderedEpoch();

        when(userRepository.existsByEmail("new@gmail.com")).thenReturn(false);
        when(userRepository.existsByPhone("0987654321")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            setPrivateField(user, "id", createdUserId);
            return user;
        });

        useCase.createUser(new CreateAdminUserCommand(
                actorId,
                "new@gmail.com",
                "0987654321",
                "secret",
                UserRole.ADMIN));

        verify(auditLogger).record("ADMIN_CREATE_USER", "SUCCESS", actorId, createdUserId, "new@gmail.com", null);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}

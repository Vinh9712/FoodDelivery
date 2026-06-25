package com.fooddelivery.customer;

import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.customer.application.command.LoginCommand;
import com.fooddelivery.customer.application.usecase.impl.LoginUseCaseImpl;
import com.fooddelivery.customer.domain.model.User;
import com.fooddelivery.customer.domain.model.enums.UserRole;
import com.fooddelivery.customer.domain.repository.RefreshTokenRepository;
import com.fooddelivery.customer.domain.repository.UserRepository;
import com.fooddelivery.customer.config.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fooddelivery.customer.api.dto.response.AuthResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoginUseCaseTests {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private JwtTokenProvider jwtTokenProvider;
    private PasswordEncoder passwordEncoder;
    private LoginUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        passwordEncoder = mock(PasswordEncoder.class);
        useCase = new LoginUseCaseImpl(
                userRepository,
                refreshTokenRepository,
                jwtTokenProvider,
                passwordEncoder);
    }

    @Test
    void login_ShouldFail_WhenPasswordIncorrect() {
        LoginCommand command = new LoginCommand(
                "test@gmail.com",
                "wrongpassword",
                "device",
                "127.0.0.1");

        User user = User.register("test@gmail.com", "0987654321", "hashed", UserRole.CUSTOMER);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThrows(BusinessRuleException.class, () -> useCase.execute(command));
    }

    @Test
    void login_ShouldStoreHashedTokenInDatabase() {
        LoginCommand command = new LoginCommand(
                "test@gmail.com",
                "password123",
                "device",
                "127.0.0.1");

        User user = User.register("test@gmail.com", "0987654321", "hashed", UserRole.CUSTOMER);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any())).thenReturn("accessToken");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(3600000L);

        AuthResponse response = useCase.execute(command);

        assertNotNull(response);
        assertNotNull(response.refreshToken());

        String expectedHash = com.fooddelivery.customer.utils.SecurityUtils.hashToken(response.refreshToken());

        org.mockito.ArgumentCaptor<com.fooddelivery.customer.domain.model.RefreshToken> tokenCaptor = org.mockito.ArgumentCaptor
                .forClass(com.fooddelivery.customer.domain.model.RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertEquals(expectedHash, tokenCaptor.getValue().getTokenHash());
    }
}
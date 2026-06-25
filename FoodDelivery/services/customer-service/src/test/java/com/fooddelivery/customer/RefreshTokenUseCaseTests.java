package com.fooddelivery.customer;

import com.fooddelivery.customer.application.command.RefreshTokenCommand;
import com.fooddelivery.customer.application.usecase.impl.RefreshTokenUseCaseImpl;
import com.fooddelivery.customer.api.dto.response.AuthResponse;
import com.fooddelivery.customer.domain.model.RefreshToken;
import com.fooddelivery.customer.domain.model.User;
import com.fooddelivery.customer.domain.model.enums.RefreshTokenStatus;
import com.fooddelivery.customer.domain.model.enums.UserRole;
import com.fooddelivery.customer.domain.repository.RefreshTokenRepository;
import com.fooddelivery.customer.config.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefreshTokenUseCaseTests {

    private RefreshTokenRepository refreshTokenRepository;
    private JwtTokenProvider jwtTokenProvider;
    private RefreshTokenUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        useCase = new RefreshTokenUseCaseImpl(refreshTokenRepository, jwtTokenProvider);
    }

    @Test
    void refreshToken_ShouldStoreHashedTokenInDatabase() {
        User user = User.register("test@gmail.com", "0987654321", "hashed", UserRole.CUSTOMER);
        String rawToken = "rawToken123";
        String hashedToken = com.fooddelivery.customer.utils.SecurityUtils.hashToken(rawToken);

        RefreshToken oldToken = RefreshToken.issue(
                user,
                hashedToken,
                Instant.now().plusSeconds(3600),
                "device",
                "127.0.0.1"
        );

        when(refreshTokenRepository.findByTokenHash(hashedToken)).thenReturn(Optional.of(oldToken));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any())).thenReturn("newAccessToken");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(3600000L);

        RefreshTokenCommand command = new RefreshTokenCommand(rawToken, "newDevice", "127.0.0.2");

        AuthResponse response = useCase.execute(command);

        assertNotNull(response);
        assertEquals("newAccessToken", response.accessToken());
        assertNotNull(response.refreshToken());
        assertNotEquals(rawToken, response.refreshToken());

        // Verify old token is revoked
        assertEquals(RefreshTokenStatus.REVOKED, oldToken.getStatus());
        assertNotNull(oldToken.getRevokedAt());

        // Verify a new token with hashed value of returned raw token is saved in DB
        String expectedNewHash = com.fooddelivery.customer.utils.SecurityUtils.hashToken(response.refreshToken());

        org.mockito.ArgumentCaptor<RefreshToken> tokenCaptor = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, atLeastOnce()).save(tokenCaptor.capture());

        boolean foundNewSavedToken = false;
        for (RefreshToken savedToken : tokenCaptor.getAllValues()) {
            if (expectedNewHash.equals(savedToken.getTokenHash())) {
                foundNewSavedToken = true;
                assertEquals(user, savedToken.getUser());
                break;
            }
        }
        assertTrue(foundNewSavedToken, "Should save the new refresh token as a SHA-256 hash in database");
    }
}
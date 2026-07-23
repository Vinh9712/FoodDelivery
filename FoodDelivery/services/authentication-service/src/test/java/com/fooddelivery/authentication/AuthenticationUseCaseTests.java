package com.fooddelivery.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.authentication.api.dto.response.AuthResponse;
import com.fooddelivery.authentication.application.command.LoginCommand;
import com.fooddelivery.authentication.application.command.LogoutCommand;
import com.fooddelivery.authentication.application.command.RefreshTokenCommand;
import com.fooddelivery.authentication.application.command.RegisterCustomerCommand;
import com.fooddelivery.authentication.application.service.UserAgentParser;
import com.fooddelivery.authentication.application.usecase.impl.LoginUseCaseImpl;
import com.fooddelivery.authentication.application.usecase.impl.LogoutUseCaseImpl;
import com.fooddelivery.authentication.application.usecase.impl.RefreshTokenUseCaseImpl;
import com.fooddelivery.authentication.application.usecase.impl.RegisterCustomerUseCaseImpl;
import com.fooddelivery.authentication.config.JwtTokenProvider;
import com.fooddelivery.authentication.domain.model.RefreshToken;
import com.fooddelivery.authentication.domain.model.User;
import com.fooddelivery.authentication.domain.model.UserSession;
import com.fooddelivery.authentication.domain.model.enums.UserRole;
import com.fooddelivery.authentication.domain.repository.RefreshTokenRepository;
import com.fooddelivery.authentication.domain.repository.UserRepository;
import com.fooddelivery.authentication.domain.repository.UserSessionRepository;
import com.fooddelivery.authentication.domain.vo.DeviceInfo;
import com.fooddelivery.authentication.infrastructure.persistence.OutboxEventRepository;
import com.fooddelivery.authentication.infrastructure.persistence.model.OutboxEvent;
import com.fooddelivery.authentication.utils.SecurityUtils;
import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.github.f4b6a3.uuid.UuidCreator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthenticationUseCaseTests {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private UserSessionRepository userSessionRepository;
    private OutboxEventRepository outboxEventRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private UserAgentParser userAgentParser;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        userSessionRepository = mock(UserSessionRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        userAgentParser = mock(UserAgentParser.class);
    }

    @Test
    void register_ShouldCreateUserAndOutboxEvent() {
        RegisterCustomerUseCaseImpl useCase = new RegisterCustomerUseCaseImpl(
                userRepository,
                outboxEventRepository,
                passwordEncoder,
                objectMapper());

        when(userRepository.existsByEmail("new@gmail.com")).thenReturn(false);
        when(userRepository.existsByPhone("0987654321")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            setPrivateField(user, "id", UuidCreator.getTimeOrderedEpoch());
            return user;
        });

        useCase.execute(new RegisterCustomerCommand(
                " New@Gmail.com ",
                "0987654321",
                "secret",
                "Nguyen Van A",
                UserRole.CUSTOMER));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("new@gmail.com", userCaptor.getValue().getEmail());
        assertEquals(UserRole.CUSTOMER, userCaptor.getValue().getRole());

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        assertEquals("user.registered", eventCaptor.getValue().getEventType());
        assertTrue(eventCaptor.getValue().getPayload().contains("\"fullName\":\"Nguyen Van A\""));
    }

    @Test
    void register_ShouldRejectDuplicateEmail() {
        RegisterCustomerUseCaseImpl useCase = new RegisterCustomerUseCaseImpl(
                userRepository,
                outboxEventRepository,
                passwordEncoder,
                objectMapper());

        when(userRepository.existsByEmail("new@gmail.com")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> useCase.execute(new RegisterCustomerCommand(
                " New@Gmail.com ",
                "0987654321",
                "secret",
                "Nguyen Van A",
                UserRole.CUSTOMER)));
        verify(userRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void login_ShouldIssueAccessAndRefreshToken() {
        LoginUseCaseImpl useCase = new LoginUseCaseImpl(
                userRepository,
                refreshTokenRepository,
                jwtTokenProvider,
                passwordEncoder,
                userAgentParser,
                userSessionRepository);
        User user = user();
        UUID sessionId = UuidCreator.getTimeOrderedEpoch();

        when(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(userAgentParser.parse("ua")).thenReturn(new DeviceInfo("DESKTOP", "Chrome", "Windows", "Computer"));
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> {
            UserSession session = invocation.getArgument(0);
            setPrivateField(session, "id", sessionId);
            return session;
        });
        when(jwtTokenProvider.generateAccessToken(eq(user.getId()), eq("new@gmail.com"), eq("CUSTOMER"), eq(sessionId)))
                .thenReturn("access");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(900000L);

        AuthResponse response = useCase.execute(new LoginCommand(" New@Gmail.com ", "secret", "ua", "127.0.0.1"));

        assertEquals("access", response.accessToken());
        assertNotNull(response.refreshToken());
        verify(userSessionRepository).markNotCurrentByUserId(user.getId());
        verify(refreshTokenRepository).save(argThat(RefreshToken::isActive));
    }

    @Test
    void login_ShouldRejectInactiveUser() {
        LoginUseCaseImpl useCase = new LoginUseCaseImpl(
                userRepository,
                refreshTokenRepository,
                jwtTokenProvider,
                passwordEncoder,
                userAgentParser,
                userSessionRepository);
        User user = user();
        user.deactivate();
        when(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.of(user));

        assertThrows(BusinessRuleException.class,
                () -> useCase.execute(new LoginCommand("new@gmail.com", "secret", "ua", "127.0.0.1")));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_ShouldRevokeOldTokenAndIssueNewToken() {
        RefreshTokenUseCaseImpl useCase = new RefreshTokenUseCaseImpl(
                refreshTokenRepository,
                jwtTokenProvider,
                userAgentParser,
                userSessionRepository);
        User user = user();
        UUID sessionId = UuidCreator.getTimeOrderedEpoch();
        RefreshToken oldToken = RefreshToken.issue(
                user,
                SecurityUtils.hashToken("old-refresh"),
                Instant.now().plus(1, ChronoUnit.DAYS),
                "ua",
                "127.0.0.1",
                sessionId);

        when(refreshTokenRepository.findByTokenHash(SecurityUtils.hashToken("old-refresh")))
                .thenReturn(Optional.of(oldToken));
        UserSession session = UserSession.create(user, "Computer", "DESKTOP", "Chrome", "Windows", "127.0.0.1");
        setPrivateField(session, "id", sessionId);
        when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(jwtTokenProvider.generateAccessToken(user.getId(), "new@gmail.com", "CUSTOMER", sessionId))
                .thenReturn("new-access");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(900000L);

        AuthResponse response = useCase.execute(new RefreshTokenCommand("old-refresh", "ua", "127.0.0.2"));

        assertEquals("new-access", response.accessToken());
        assertFalse(oldToken.isActive());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void logout_ShouldRevokeRefreshTokenHash() {
        LogoutUseCaseImpl useCase = new LogoutUseCaseImpl(refreshTokenRepository, userSessionRepository);
        User user = user();
        UUID sessionId = UuidCreator.getTimeOrderedEpoch();
        RefreshToken token = RefreshToken.issue(
                user,
                SecurityUtils.hashToken("refresh"),
                Instant.now().plus(1, ChronoUnit.DAYS),
                "ua",
                "127.0.0.1",
                sessionId);
        UserSession session = UserSession.create(user, "Computer", "DESKTOP", "Chrome", "Windows", "127.0.0.1");
        setPrivateField(session, "id", sessionId);
        when(refreshTokenRepository.findByTokenHash(SecurityUtils.hashToken("refresh")))
                .thenReturn(Optional.of(token));
        when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        useCase.execute(new LogoutCommand("refresh"));

        verify(refreshTokenRepository).revokeByTokenHash(SecurityUtils.hashToken("refresh"));
        verify(refreshTokenRepository).revokeAllBySessionId(sessionId);
        verify(userSessionRepository).save(session);
        assertTrue(session.isDeleted());
    }

    @Test
    void refreshReuse_ShouldRevokeCompromisedSession() {
        RefreshTokenUseCaseImpl useCase = new RefreshTokenUseCaseImpl(
                refreshTokenRepository,
                jwtTokenProvider,
                userAgentParser,
                userSessionRepository);
        User user = user();
        UUID sessionId = UuidCreator.getTimeOrderedEpoch();
        RefreshToken reusedToken = RefreshToken.issue(
                user,
                SecurityUtils.hashToken("reused-refresh"),
                Instant.now().plus(1, ChronoUnit.DAYS),
                "ua",
                "127.0.0.1",
                sessionId);
        reusedToken.revoke();
        UserSession session = UserSession.create(user, "Computer", "DESKTOP", "Chrome", "Windows", "127.0.0.1");
        setPrivateField(session, "id", sessionId);
        when(refreshTokenRepository.findByTokenHash(SecurityUtils.hashToken("reused-refresh")))
                .thenReturn(Optional.of(reusedToken));
        when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThrows(BusinessRuleException.class, () -> useCase.execute(
                new RefreshTokenCommand("reused-refresh", "ua", "127.0.0.2")));

        verify(refreshTokenRepository).revokeAllBySessionId(sessionId);
        verify(userSessionRepository).save(session);
        assertTrue(session.isDeleted());
    }

    private User user() {
        User user = User.register("new@gmail.com", "0987654321", "hash", UserRole.CUSTOMER);
        setPrivateField(user, "id", UuidCreator.getTimeOrderedEpoch());
        return user;
    }

    private static ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
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

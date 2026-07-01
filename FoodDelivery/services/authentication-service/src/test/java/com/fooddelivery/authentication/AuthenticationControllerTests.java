package com.fooddelivery.authentication;

import com.fooddelivery.authentication.api.controller.AuthenticationController;
import com.fooddelivery.authentication.api.dto.request.LoginRequest;
import com.fooddelivery.authentication.application.service.LoginRateLimiter;
import com.fooddelivery.authentication.application.service.SecurityAuditLogger;
import com.fooddelivery.authentication.application.usecase.LoginUseCase;
import com.fooddelivery.authentication.application.usecase.LogoutUseCase;
import com.fooddelivery.authentication.application.usecase.RefreshTokenUseCase;
import com.fooddelivery.authentication.application.usecase.RegisterCustomerUseCase;
import com.fooddelivery.authentication.utils.RealIPExtractor;
import com.fooddelivery.commonweb.exception.BusinessRuleException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthenticationControllerTests {

    @Test
    void login_ShouldAuditBlockedAttempt_WhenRateLimiterRejects() {
        RegisterCustomerUseCase registerCustomerUseCase = mock(RegisterCustomerUseCase.class);
        LoginUseCase loginUseCase = mock(LoginUseCase.class);
        RefreshTokenUseCase refreshTokenUseCase = mock(RefreshTokenUseCase.class);
        LogoutUseCase logoutUseCase = mock(LogoutUseCase.class);
        RealIPExtractor realIPExtractor = mock(RealIPExtractor.class);
        LoginRateLimiter loginRateLimiter = mock(LoginRateLimiter.class);
        SecurityAuditLogger auditLogger = mock(SecurityAuditLogger.class);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        AuthenticationController controller = new AuthenticationController(
                registerCustomerUseCase,
                loginUseCase,
                refreshTokenUseCase,
                logoutUseCase,
                realIPExtractor,
                loginRateLimiter,
                auditLogger);
        LoginRequest request = new LoginRequest("new@gmail.com", "password");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(realIPExtractor.extract(httpServletRequest)).thenReturn("127.0.0.1");
        doThrow(new BusinessRuleException("Too many failed login attempts. Try again later."))
                .when(loginRateLimiter).checkAllowed("new@gmail.com", "127.0.0.1");

        assertThrows(BusinessRuleException.class, () -> controller.login(request, httpServletRequest));

        verify(auditLogger).record("LOGIN", "BLOCKED", null, null, "new@gmail.com", "127.0.0.1");
        verifyNoInteractions(loginUseCase);
    }
}

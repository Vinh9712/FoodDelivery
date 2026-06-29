package com.fooddelivery.authentication.api.controller;

import com.fooddelivery.commonweb.response.ApiResponse;
import com.fooddelivery.authentication.api.dto.request.LoginRequest;
import com.fooddelivery.authentication.api.dto.request.RefreshTokenRequest;
import com.fooddelivery.authentication.api.dto.request.RegisterRequest;
import com.fooddelivery.authentication.api.dto.response.AuthResponse;
import com.fooddelivery.authentication.api.dto.response.UserRegistrationResponse;
import com.fooddelivery.authentication.application.command.LoginCommand;
import com.fooddelivery.authentication.application.command.LogoutCommand;
import com.fooddelivery.authentication.application.command.RefreshTokenCommand;
import com.fooddelivery.authentication.application.command.RegisterCustomerCommand;
import com.fooddelivery.authentication.application.service.LoginRateLimiter;
import com.fooddelivery.authentication.application.service.SecurityAuditLogger;
import com.fooddelivery.authentication.utils.RealIPExtractor;
import com.fooddelivery.authentication.application.usecase.LoginUseCase;
import com.fooddelivery.authentication.application.usecase.LogoutUseCase;
import com.fooddelivery.authentication.application.usecase.RefreshTokenUseCase;
import com.fooddelivery.authentication.application.usecase.RegisterCustomerUseCase;
import com.fooddelivery.authentication.domain.model.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ "/api/v1/auth", "/auth" })
public class AuthenticationController {

    private final RegisterCustomerUseCase registerCustomerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RealIPExtractor realIPExtractor;
    private final LoginRateLimiter loginRateLimiter;
    private final SecurityAuditLogger auditLogger;

    public AuthenticationController(
            RegisterCustomerUseCase registerCustomerUseCase,
            LoginUseCase loginUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase,
            RealIPExtractor realIPExtractor,
            LoginRateLimiter loginRateLimiter,
            SecurityAuditLogger auditLogger) {
        this.registerCustomerUseCase = registerCustomerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.realIPExtractor = realIPExtractor;
        this.loginRateLimiter = loginRateLimiter;
        this.auditLogger = auditLogger;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserRegistrationResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpServletRequest) {
        RegisterCustomerCommand command = new RegisterCustomerCommand(
                request.email(),
                request.phone(),
                request.password(),
                request.fullName(),
                UserRole.CUSTOMER);
        String ipAddress = realIPExtractor.extract(httpServletRequest);
        try {
            UserRegistrationResponse response = registerCustomerUseCase.execute(command);
            auditLogger.record("REGISTER", "SUCCESS", null, response.userId(), response.email(), ipAddress);
            return ResponseEntity.ok(ApiResponse.ok(response, "Registration successful"));
        } catch (RuntimeException ex) {
            auditLogger.record("REGISTER", "FAILURE", null, null, request.email(), ipAddress);
            throw ex;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest) {
        String deviceInfo = httpServletRequest.getHeader("User-Agent");
        String ipAddress = realIPExtractor.extract(httpServletRequest);
        loginRateLimiter.checkAllowed(request.email(), ipAddress);
        LoginCommand command = new LoginCommand(
                request.email(),
                request.password(),
                deviceInfo,
                ipAddress);
        try {
            AuthResponse response = loginUseCase.execute(command);
            loginRateLimiter.reset(request.email(), ipAddress);
            auditLogger.record("LOGIN", "SUCCESS", null, null, request.email(), ipAddress);
            return ResponseEntity.ok(ApiResponse.ok(response, "Login successful"));
        } catch (RuntimeException ex) {
            loginRateLimiter.recordFailure(request.email(), ipAddress);
            auditLogger.record("LOGIN", "FAILURE", null, null, request.email(), ipAddress);
            throw ex;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpServletRequest) {
        String deviceInfo = httpServletRequest.getHeader("User-Agent");
        String ipAddress = realIPExtractor.extract(httpServletRequest);
        RefreshTokenCommand command = new RefreshTokenCommand(
                request.refreshToken(),
                deviceInfo,
                ipAddress);
        try {
            AuthResponse response = refreshTokenUseCase.execute(command);
            auditLogger.record("REFRESH_TOKEN", "SUCCESS", null, null, null, ipAddress);
            return ResponseEntity.ok(ApiResponse.ok(response, "Token refreshed"));
        } catch (RuntimeException ex) {
            auditLogger.record("REFRESH_TOKEN", "FAILURE", null, null, null, ipAddress);
            throw ex;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpServletRequest) {
        LogoutCommand command = new LogoutCommand(request.refreshToken());
        logoutUseCase.execute(command);
        auditLogger.record("LOGOUT", "SUCCESS", null, null, null, realIPExtractor.extract(httpServletRequest));
        return ResponseEntity.ok(ApiResponse.ok(null, "Logout successful"));
    }
}

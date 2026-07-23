package com.fooddelivery.authentication.api.controller;

import com.fooddelivery.commonweb.response.ApiResponse;
import com.fooddelivery.authentication.api.dto.request.ChangePasswordRequest;
import com.fooddelivery.authentication.api.dto.request.ForgotPasswordRequest;
import com.fooddelivery.authentication.api.dto.request.LoginRequest;
import com.fooddelivery.authentication.api.dto.request.RefreshTokenRequest;
import com.fooddelivery.authentication.api.dto.request.RegisterRequest;
import com.fooddelivery.authentication.api.dto.request.ResetPasswordRequest;
import com.fooddelivery.authentication.api.dto.response.AuthResponse;
import com.fooddelivery.authentication.api.dto.response.UserRegistrationResponse;
import com.fooddelivery.authentication.application.command.ChangePasswordCommand;
import com.fooddelivery.authentication.application.command.ForgotPasswordCommand;
import com.fooddelivery.authentication.application.command.LoginCommand;
import com.fooddelivery.authentication.application.command.LogoutCommand;
import com.fooddelivery.authentication.application.command.RefreshTokenCommand;
import com.fooddelivery.authentication.application.command.RegisterCustomerCommand;
import com.fooddelivery.authentication.application.command.ResetPasswordCommand;
import com.fooddelivery.authentication.application.service.LoginRateLimiter;
import com.fooddelivery.authentication.application.service.SecurityAuditLogger;
import com.fooddelivery.authentication.utils.RealIPExtractor;
import com.fooddelivery.authentication.application.usecase.LoginUseCase;
import com.fooddelivery.authentication.application.usecase.LogoutUseCase;
import com.fooddelivery.authentication.application.usecase.PasswordUseCase;
import com.fooddelivery.authentication.application.usecase.RefreshTokenUseCase;
import com.fooddelivery.authentication.application.usecase.RegisterCustomerUseCase;
import com.fooddelivery.authentication.config.UserPrincipal;
import com.fooddelivery.authentication.domain.model.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ "/api/v1/auth", "/auth" })
public class AuthenticationController {

    private final RegisterCustomerUseCase registerCustomerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final PasswordUseCase passwordUseCase;
    private final RealIPExtractor realIPExtractor;
    private final LoginRateLimiter loginRateLimiter;
    private final SecurityAuditLogger auditLogger;

    public AuthenticationController(
            RegisterCustomerUseCase registerCustomerUseCase,
            LoginUseCase loginUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase,
            PasswordUseCase passwordUseCase,
            RealIPExtractor realIPExtractor,
            LoginRateLimiter loginRateLimiter,
            SecurityAuditLogger auditLogger) {
        this.registerCustomerUseCase = registerCustomerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.passwordUseCase = passwordUseCase;
        this.realIPExtractor = realIPExtractor;
        this.loginRateLimiter = loginRateLimiter;
        this.auditLogger = auditLogger;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserRegistrationResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpServletRequest) {
        return registerWithRole(request, UserRole.CUSTOMER, httpServletRequest, "Registration successful");
    }

    /**
     * Self-register a driver account (role {@code DRIVER}).
     * After login, complete profile via {@code PUT /api/v1/drivers/me}.
     */
    @PostMapping("/register-driver")
    public ResponseEntity<ApiResponse<UserRegistrationResponse>> registerDriver(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpServletRequest) {
        return registerWithRole(request, UserRole.DRIVER, httpServletRequest, "Driver registration successful");
    }

    /**
     * Self-register a restaurant owner account (role {@code RESTAURANT_OWNER}).
     */
    @PostMapping("/register-owner")
    public ResponseEntity<ApiResponse<UserRegistrationResponse>> registerOwner(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpServletRequest) {
        return registerWithRole(
                request, UserRole.RESTAURANT_OWNER, httpServletRequest, "Owner registration successful");
    }

    private ResponseEntity<ApiResponse<UserRegistrationResponse>> registerWithRole(
            RegisterRequest request,
            UserRole role,
            HttpServletRequest httpServletRequest,
            String successMessage) {
        RegisterCustomerCommand command = new RegisterCustomerCommand(
                request.email(),
                request.phone(),
                request.password(),
                request.fullName(),
                role);
        String ipAddress = realIPExtractor.extract(httpServletRequest);
        try {
            UserRegistrationResponse response = registerCustomerUseCase.execute(command);
            auditLogger.record("REGISTER", "SUCCESS", null, response.userId(), response.email(), ipAddress);
            return ResponseEntity.ok(ApiResponse.ok(response, successMessage));
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
        try {
            loginRateLimiter.checkAllowed(request.email(), ipAddress);
        } catch (RuntimeException ex) {
            auditLogger.record("LOGIN", "BLOCKED", null, null, request.email(), ipAddress);
            throw ex;
        }
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

    /**
     * Request a password-reset token. Always returns a generic success message.
     * Local/dev: token is written to auth-service logs (no mailer yet).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpServletRequest) {
        String ip = realIPExtractor.extract(httpServletRequest);
        passwordUseCase.forgotPassword(new ForgotPasswordCommand(request.email()));
        auditLogger.record("FORGOT_PASSWORD", "SUCCESS", null, null, request.email(), ip);
        return ResponseEntity.ok(ApiResponse.ok(null,
                "If an account exists for that email, password reset instructions have been issued"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpServletRequest) {
        String ip = realIPExtractor.extract(httpServletRequest);
        try {
            passwordUseCase.resetPassword(new ResetPasswordCommand(request.token(), request.newPassword()));
            auditLogger.record("RESET_PASSWORD", "SUCCESS", null, null, null, ip);
            return ResponseEntity.ok(ApiResponse.ok(null, "Password has been reset; please log in again"));
        } catch (RuntimeException ex) {
            auditLogger.record("RESET_PASSWORD", "FAILURE", null, null, null, ip);
            throw ex;
        }
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpServletRequest) {
        String ip = realIPExtractor.extract(httpServletRequest);
        try {
            passwordUseCase.changePassword(new ChangePasswordCommand(
                    principal.userId(), request.oldPassword(), request.newPassword()));
            auditLogger.record("CHANGE_PASSWORD", "SUCCESS", principal.userId(), principal.userId(),
                    principal.email(), ip);
            return ResponseEntity.ok(ApiResponse.ok(null,
                    "Password changed; please log in again on all devices"));
        } catch (RuntimeException ex) {
            auditLogger.record("CHANGE_PASSWORD", "FAILURE", principal.userId(), principal.userId(),
                    principal.email(), ip);
            throw ex;
        }
    }
}

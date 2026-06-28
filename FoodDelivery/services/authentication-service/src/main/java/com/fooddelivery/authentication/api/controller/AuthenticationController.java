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

    public AuthenticationController(
            RegisterCustomerUseCase registerCustomerUseCase,
            LoginUseCase loginUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase,
            RealIPExtractor realIPExtractor) {
        this.registerCustomerUseCase = registerCustomerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.realIPExtractor = realIPExtractor;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserRegistrationResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterCustomerCommand command = new RegisterCustomerCommand(
                request.email(),
                request.phone(),
                request.password(),
                request.fullName(),
                UserRole.CUSTOMER);
        UserRegistrationResponse response = registerCustomerUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.ok(response, "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest) {
        String deviceInfo = httpServletRequest.getHeader("User-Agent");
        String ipAddress = realIPExtractor.extract(httpServletRequest);
        LoginCommand command = new LoginCommand(
                request.email(),
                request.password(),
                deviceInfo,
                ipAddress);
        AuthResponse response = loginUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.ok(response, "Login successful"));
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
        AuthResponse response = refreshTokenUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.ok(response, "Token refreshed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        LogoutCommand command = new LogoutCommand(request.refreshToken());
        logoutUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.ok(null, "Logout successful"));
    }
}

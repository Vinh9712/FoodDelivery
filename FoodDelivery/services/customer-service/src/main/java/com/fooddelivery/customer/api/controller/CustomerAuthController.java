package com.fooddelivery.customer.api.controller;

import com.fooddelivery.commonweb.response.ApiResponse;
import com.fooddelivery.customer.api.dto.request.LoginRequest;
import com.fooddelivery.customer.api.dto.request.RefreshTokenRequest;
import com.fooddelivery.customer.api.dto.request.RegisterRequest;
import com.fooddelivery.customer.api.dto.response.AuthResponse;
import com.fooddelivery.customer.api.dto.response.CustomerProfileResponse;
import com.fooddelivery.customer.application.command.LoginCommand;
import com.fooddelivery.customer.application.command.LogoutCommand;
import com.fooddelivery.customer.application.command.RefreshTokenCommand;
import com.fooddelivery.customer.application.command.RegisterCustomerCommand;
import com.fooddelivery.customer.application.usecase.LoginUseCase;
import com.fooddelivery.customer.application.usecase.LogoutUseCase;
import com.fooddelivery.customer.application.usecase.RefreshTokenUseCase;
import com.fooddelivery.customer.application.usecase.RegisterCustomerUseCase;
import com.fooddelivery.customer.domain.model.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ "/api/v1/auth", "/auth" })
public class CustomerAuthController {

    private final RegisterCustomerUseCase registerCustomerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;

    public CustomerAuthController(
            RegisterCustomerUseCase registerCustomerUseCase,
            LoginUseCase loginUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase) {
        this.registerCustomerUseCase = registerCustomerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterCustomerCommand command = new RegisterCustomerCommand(
                request.email(),
                request.phone(),
                request.password(),
                request.fullName(),
                UserRole.CUSTOMER);
        CustomerProfileResponse response = registerCustomerUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.ok(response, "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest) {
        String deviceInfo = httpServletRequest.getHeader("User-Agent");
        String ipAddress = httpServletRequest.getRemoteAddr();
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
        String ipAddress = httpServletRequest.getRemoteAddr();
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

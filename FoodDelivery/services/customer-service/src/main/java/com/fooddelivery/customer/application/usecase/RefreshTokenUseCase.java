package com.fooddelivery.customer.application.usecase;

import com.fooddelivery.customer.application.command.RefreshTokenCommand;
import com.fooddelivery.customer.api.dto.response.AuthResponse;

public interface RefreshTokenUseCase {
    AuthResponse execute(RefreshTokenCommand command);
}
package com.fooddelivery.authentication.application.usecase;

import com.fooddelivery.authentication.application.command.RefreshTokenCommand;
import com.fooddelivery.authentication.api.dto.response.AuthResponse;

public interface RefreshTokenUseCase {
    AuthResponse execute(RefreshTokenCommand command);
}
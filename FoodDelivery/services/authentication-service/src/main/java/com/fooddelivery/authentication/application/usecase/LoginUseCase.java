package com.fooddelivery.authentication.application.usecase;

import com.fooddelivery.authentication.application.command.LoginCommand;
import com.fooddelivery.authentication.api.dto.response.AuthResponse;

public interface LoginUseCase {
    AuthResponse execute(LoginCommand command);
}
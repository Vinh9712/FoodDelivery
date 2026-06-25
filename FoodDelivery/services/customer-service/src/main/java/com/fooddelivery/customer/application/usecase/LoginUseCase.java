package com.fooddelivery.customer.application.usecase;

import com.fooddelivery.customer.application.command.LoginCommand;
import com.fooddelivery.customer.api.dto.response.AuthResponse;

public interface LoginUseCase {
    AuthResponse execute(LoginCommand command);
}
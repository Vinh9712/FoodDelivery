package com.fooddelivery.authentication.application.usecase;

import com.fooddelivery.authentication.application.command.RegisterCustomerCommand;
import com.fooddelivery.authentication.api.dto.response.UserRegistrationResponse;

public interface RegisterCustomerUseCase {
    UserRegistrationResponse execute(RegisterCustomerCommand command);
}

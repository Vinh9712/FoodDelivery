package com.fooddelivery.customer.application.usecase;

import com.fooddelivery.customer.application.command.RegisterCustomerCommand;
import com.fooddelivery.customer.api.dto.response.CustomerProfileResponse;

public interface RegisterCustomerUseCase {
    CustomerProfileResponse execute(RegisterCustomerCommand command);
}
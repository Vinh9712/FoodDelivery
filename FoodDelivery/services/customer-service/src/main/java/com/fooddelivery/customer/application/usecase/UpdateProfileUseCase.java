package com.fooddelivery.customer.application.usecase;

import com.fooddelivery.customer.application.command.UpdateProfileCommand;
import com.fooddelivery.customer.api.dto.response.CustomerProfileResponse;

import java.util.UUID;

public interface UpdateProfileUseCase {
    CustomerProfileResponse execute(UpdateProfileCommand command);
    CustomerProfileResponse getProfile(UUID userId);
}
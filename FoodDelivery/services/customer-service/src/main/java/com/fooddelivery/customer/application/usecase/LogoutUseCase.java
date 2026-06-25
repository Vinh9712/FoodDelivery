package com.fooddelivery.customer.application.usecase;

import com.fooddelivery.customer.application.command.LogoutCommand;

public interface LogoutUseCase {
    void execute(LogoutCommand command);
}
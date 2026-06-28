package com.fooddelivery.authentication.application.usecase;

import com.fooddelivery.authentication.application.command.LogoutCommand;

public interface LogoutUseCase {
    void execute(LogoutCommand command);
}
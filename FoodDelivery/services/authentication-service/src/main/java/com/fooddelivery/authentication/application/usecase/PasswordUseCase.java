package com.fooddelivery.authentication.application.usecase;

import com.fooddelivery.authentication.application.command.ChangePasswordCommand;
import com.fooddelivery.authentication.application.command.ForgotPasswordCommand;
import com.fooddelivery.authentication.application.command.ResetPasswordCommand;

public interface PasswordUseCase {
    void forgotPassword(ForgotPasswordCommand command);

    void resetPassword(ResetPasswordCommand command);

    void changePassword(ChangePasswordCommand command);
}

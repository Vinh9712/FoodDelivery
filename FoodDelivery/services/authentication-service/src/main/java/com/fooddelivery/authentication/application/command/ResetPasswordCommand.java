package com.fooddelivery.authentication.application.command;

public record ResetPasswordCommand(String token, String newPassword) {
}

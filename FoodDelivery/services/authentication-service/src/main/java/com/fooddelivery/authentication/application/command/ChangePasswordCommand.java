package com.fooddelivery.authentication.application.command;

import java.util.UUID;

public record ChangePasswordCommand(UUID userId, String oldPassword, String newPassword) {
}

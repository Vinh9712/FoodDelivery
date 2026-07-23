package com.fooddelivery.authentication.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank String token,

        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}$",
                message = "Password must be at least 8 characters, include 1 uppercase letter, 1 digit and 1 special character"
        )
        String newPassword
) {
}

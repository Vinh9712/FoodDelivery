package com.fooddelivery.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelRequest {
    @NotBlank(message = "Reason is required")
    private String reason;
}
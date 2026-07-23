package com.fooddelivery.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReorderRequest {
    private String deliveryAddress;
    private String note;
}
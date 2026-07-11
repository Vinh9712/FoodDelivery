package com.fooddelivery.restaurant.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {
    @JsonIgnore
    private UUID customerId;

    @NotNull
    private UUID orderId;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    private String comment;
}

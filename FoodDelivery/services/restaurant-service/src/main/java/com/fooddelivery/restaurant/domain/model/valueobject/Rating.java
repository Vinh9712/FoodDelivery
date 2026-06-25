package com.fooddelivery.restaurant.domain.model.valueobject;

import com.fooddelivery.restaurant.domain.exception.InvalidRatingException;
import java.math.BigDecimal;

public record Rating(BigDecimal value) {
    public Rating {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new InvalidRatingException(value);
        }
    }
}

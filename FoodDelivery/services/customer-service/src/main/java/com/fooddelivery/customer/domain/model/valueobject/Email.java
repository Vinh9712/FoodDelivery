package com.fooddelivery.customer.domain.model.valueobject;

import com.fooddelivery.customer.domain.exception.InvalidEmailException;

public record Email(String value) {
    public Email {
        if (value == null || !value.matches("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")) {
            throw new InvalidEmailException(value);
        }
    }
}

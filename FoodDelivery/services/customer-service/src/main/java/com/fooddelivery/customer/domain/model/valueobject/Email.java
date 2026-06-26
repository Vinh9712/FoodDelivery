package com.fooddelivery.customer.domain.model.valueobject;

import java.util.regex.Pattern;

public record Email(String value) {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[A-Za-z]{2,}$");

    public Email {
        if (value == null || !EMAIL_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }
        value = value.trim().toLowerCase();
    }
}

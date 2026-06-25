package com.fooddelivery.customer.domain.model.valueobject;

import java.util.regex.Pattern;

public record PhoneNumber(String value) {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{9,15}$");

    public PhoneNumber {
        if (value == null || !PHONE_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Invalid phone number: " + value);
        }
        value = value.trim();
    }
}

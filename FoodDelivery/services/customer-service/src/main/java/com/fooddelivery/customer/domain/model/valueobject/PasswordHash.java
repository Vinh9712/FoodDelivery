package com.fooddelivery.customer.domain.model.valueobject;

import org.springframework.security.crypto.bcrypt.BCrypt;

public record PasswordHash(String value) {
    public boolean matches(String rawPassword) {
        return BCrypt.checkpw(rawPassword, value);
    }
    public static PasswordHash hash(String rawPassword) {
        return new PasswordHash(BCrypt.hashpw(rawPassword, BCrypt.gensalt()));
    }
}

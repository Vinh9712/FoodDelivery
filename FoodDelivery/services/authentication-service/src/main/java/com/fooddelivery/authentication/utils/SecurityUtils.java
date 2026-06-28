package com.fooddelivery.authentication.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class SecurityUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecurityUtils() {}

    /**
     * Generates a secure, high-entropy 256-bit random token encoded as a Base64 URL-safe string.
     */
    public static String generateRandomToken() {
        byte[] bytes = new byte[32]; // 32 bytes = 256 bits of entropy
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes the raw token string using SHA-256 and returns a Base64-encoded digest.
     */
    public static String hashToken(String token) {
        if (token == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

package com.fooddelivery.authentication.application.service;

import com.fooddelivery.commonweb.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int WINDOW_MINUTES = 15;
    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public void checkAllowed(String email, String ipAddress) {
        String key = key(email, ipAddress);
        AttemptWindow window = attempts.get(key);
        if (window == null || window.expiresAt().isBefore(Instant.now())) {
            attempts.remove(key);
            return;
        }
        if (window.failedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw new BusinessRuleException("Too many failed login attempts. Try again later.");
        }
    }

    public void recordFailure(String email, String ipAddress) {
        String key = key(email, ipAddress);
        Instant now = Instant.now();
        attempts.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new AttemptWindow(1, now.plus(WINDOW_MINUTES, ChronoUnit.MINUTES));
            }
            return new AttemptWindow(current.failedAttempts() + 1, current.expiresAt());
        });
    }

    public void reset(String email, String ipAddress) {
        attempts.remove(key(email, ipAddress));
    }

    private String key(String email, String ipAddress) {
        // ponytail: in-memory per-instance limiter; move to Redis when multiple auth-service replicas run.
        return (email == null ? "" : email.trim().toLowerCase(Locale.ROOT)) + "|" + (ipAddress == null ? "" : ipAddress);
    }

    private record AttemptWindow(int failedAttempts, Instant expiresAt) {}
}

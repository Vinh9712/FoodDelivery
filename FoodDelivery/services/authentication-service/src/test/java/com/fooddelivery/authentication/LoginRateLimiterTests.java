package com.fooddelivery.authentication;

import com.fooddelivery.authentication.application.service.LoginRateLimiter;
import com.fooddelivery.commonweb.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTests {

    @Test
    void checkAllowed_ShouldBlockAfterFiveFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("new@gmail.com", "127.0.0.1");
        }

        assertThrows(BusinessRuleException.class,
                () -> limiter.checkAllowed("new@gmail.com", "127.0.0.1"));
    }

    @Test
    void reset_ShouldAllowAgain() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("new@gmail.com", "127.0.0.1");
        }

        limiter.reset("new@gmail.com", "127.0.0.1");

        limiter.checkAllowed("new@gmail.com", "127.0.0.1");
    }

    @Test
    void evictExpiredAttempts_ShouldRemoveStaleBucketsWithoutSameKeyReuse() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = newLimiter(clock);
        limiter.recordFailure("unique@gmail.com", "127.0.0.1");

        clock.advance(Duration.ofMinutes(16));
        limiter.evictExpiredAttempts();

        assertEquals(0, trackedBucketCount(limiter));
    }

    private static int trackedBucketCount(LoginRateLimiter limiter) {
        try {
            Field attempts = LoginRateLimiter.class.getDeclaredField("attempts");
            attempts.setAccessible(true);
            return ((Map<?, ?>) attempts.get(limiter)).size();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static LoginRateLimiter newLimiter(Clock clock) {
        try {
            var constructor = LoginRateLimiter.class.getDeclaredConstructor(Clock.class);
            constructor.setAccessible(true);
            return constructor.newInstance(clock);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

package com.fooddelivery.authentication;

import com.fooddelivery.authentication.application.service.LoginRateLimiter;
import com.fooddelivery.commonweb.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

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
}

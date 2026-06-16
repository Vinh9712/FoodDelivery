package com.fooddelivery.payment.domain.exception;

import java.math.BigDecimal;

public class NegativeMoneyException extends RuntimeException {
    public NegativeMoneyException(BigDecimal amount) {
        super("Money amount cannot be negative: " + amount);
    }
}

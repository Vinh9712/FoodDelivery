package com.fooddelivery.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PaymentClockConfiguration {

    @Bean
    Clock paymentClock() {
        return Clock.systemUTC();
    }
}

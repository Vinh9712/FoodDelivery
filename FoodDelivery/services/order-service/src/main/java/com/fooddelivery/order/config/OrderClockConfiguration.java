package com.fooddelivery.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class OrderClockConfiguration {

    @Bean
    Clock orderClock() {
        return Clock.systemUTC();
    }
}

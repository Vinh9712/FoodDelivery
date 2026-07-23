package com.fooddelivery.authentication.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuthClockConfiguration {

    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }
}

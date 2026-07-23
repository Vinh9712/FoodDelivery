package com.fooddelivery.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class NotificationClockConfiguration {

    @Bean
    Clock notificationClock() {
        return Clock.systemUTC();
    }
}

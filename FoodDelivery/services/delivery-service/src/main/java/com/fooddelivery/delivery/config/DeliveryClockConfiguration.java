package com.fooddelivery.delivery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DeliveryClockConfiguration {

    @Bean
    Clock deliveryClock() {
        return Clock.systemUTC();
    }
}

package com.fooddelivery.restaurant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class RestaurantClockConfiguration {

    @Bean
    Clock restaurantClock(@Value("${app.restaurant.business-zone:Asia/Ho_Chi_Minh}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}

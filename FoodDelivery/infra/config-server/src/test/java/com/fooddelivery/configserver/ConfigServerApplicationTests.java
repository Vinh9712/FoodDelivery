package com.fooddelivery.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.fooddelivery.config.ConfigServerApplication;

@SpringBootTest(
        classes = ConfigServerApplication.class,
        properties = {
                "spring.profiles.active=native",
                "spring.cloud.bus.enabled=false",
                "spring.cloud.config.enabled=false"
        })
class ConfigServerApplicationTests {

    @Test
    void contextLoads() {
    }

}

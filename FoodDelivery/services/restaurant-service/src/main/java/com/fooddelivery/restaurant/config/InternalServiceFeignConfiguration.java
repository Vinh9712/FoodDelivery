package com.fooddelivery.restaurant.config;

import com.fooddelivery.security.InternalServiceAuthenticationFilter;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class InternalServiceFeignConfiguration {

    @Bean
    RequestInterceptor internalServiceCredentialInterceptor(
            @Value("${app.security.internal-service-secret:}") String internalServiceSecret) {
        return template -> {
            if (!StringUtils.hasText(internalServiceSecret)) {
                throw new IllegalStateException("app.security.internal-service-secret is required for internal calls");
            }
            template.header(InternalServiceAuthenticationFilter.HEADER_NAME, internalServiceSecret);
        };
    }
}

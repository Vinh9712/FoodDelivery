package com.fooddelivery.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@AutoConfiguration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ResourceServerSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    FoodDeliveryJwtAuthenticationConverter jwtAuthenticationTokenConverter() {
        return new FoodDeliveryJwtAuthenticationConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwt().getJwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getJwt().getIssuer()),
                new AudienceValidator(properties.getJwt().getAudience())
        ));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    InternalServiceAuthenticationFilter internalServiceAuthenticationFilter(SecurityProperties properties) {
        return new InternalServiceAuthenticationFilter(properties.getInternalServiceSecret());
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain resourceServerSecurityFilterChain(
            HttpSecurity http,
            SecurityProperties properties,
            FoodDeliveryJwtAuthenticationConverter jwtConverter,
            InternalServiceAuthenticationFilter internalFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(properties.getPublicPaths().toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                properties.getPublicGetPaths().toArray(String[]::new)).permitAll()
                        .requestMatchers("/internal/**").hasRole("SERVICE")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .addFilterBefore(internalFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}

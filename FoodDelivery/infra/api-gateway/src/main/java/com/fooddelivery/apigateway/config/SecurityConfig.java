package com.fooddelivery.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.List;

/**
 * Reactive security for the gateway.
 *
 * <p>By default the gateway acts as an OAuth2 <b>Resource Server</b>: every request
 * needs a valid authentication-service JWT, except the configured public paths and CORS
 * pre-flight requests.</p>
 *
 * <p>Run with {@code --spring.profiles.active=insecure} to disable auth entirely -
 * handy for local development before authentication-service is available.</p>
 */
@Configuration
@EnableWebFluxSecurity
@RefreshScope
public class SecurityConfig {

    @Value("${app.security.public-paths:/actuator/**}")
    private List<String> publicPaths;

    @Bean
    @Profile("!insecure")
    public SecurityWebFilterChain secureFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(publicPaths.toArray(String[]::new)).permitAll()
                        .pathMatchers(HttpMethod.GET,
                                "/api/v1/restaurants/**",
                                "/api/v1/items/**",
                                "/api/v1/categories/**",
                                "/api/v1/reviews/**").permitAll()
                        .pathMatchers("/actuator/gateway/**", "/actuator/refresh").hasRole("ADMIN")
                        .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/v1/restaurants/*/reviews").hasAnyRole("CUSTOMER", "ADMIN")
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/restaurants",
                                "/api/v1/restaurants/*/items",
                                "/api/v1/restaurants/*/categories").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.PUT,
                                "/api/v1/restaurants/**",
                                "/api/v1/items/**",
                                "/api/v1/categories/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.PATCH,
                                "/api/v1/restaurants/**",
                                "/api/v1/items/**",
                                "/api/v1/categories/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.DELETE,
                                "/api/v1/restaurants/**",
                                "/api/v1/items/**",
                                "/api/v1/categories/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/v1/orders/**").hasRole("CUSTOMER")
                        .pathMatchers(HttpMethod.POST, "/api/v1/deliveries/*/assign-driver/*").hasRole("ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(
                                new GatewayJwtAuthenticationConverter()))));
        return http.build();
    }

    @Bean
    @Profile("!insecure")
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${app.security.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${app.security.jwt.issuer}") String issuer,
            @Value("${app.security.jwt.audience}") String audience) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(audience)
        ));
        return decoder;
    }

    @Bean
    @Profile("insecure")
    public SecurityWebFilterChain insecureFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll());
        return http.build();
    }
}

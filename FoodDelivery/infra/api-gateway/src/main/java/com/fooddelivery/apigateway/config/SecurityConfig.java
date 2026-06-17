package com.fooddelivery.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.List;

/**
 * Reactive security for the gateway.
 *
 * <p>By default the gateway acts as an OAuth2 <b>Resource Server</b>: every request
 * needs a valid Keycloak-issued JWT, except the configured public paths and CORS
 * pre-flight requests.</p>
 *
 * <p>Run with {@code --spring.profiles.active=insecure} to disable auth entirely —
 * handy for local development before Keycloak is available.</p>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.security.public-paths:/actuator/**}")
    private List<String> publicPaths;

    /** Secure chain – validates JWTs against the configured Keycloak issuer. */
    @Bean
    @Profile("!insecure")
    public SecurityWebFilterChain secureFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(publicPaths.toArray(String[]::new)).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }

    /** Insecure chain – permits everything (local dev without Keycloak). */
    @Bean
    @Profile("insecure")
    public SecurityWebFilterChain insecureFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll());
        return http.build();
    }
}

package com.fooddelivery.apigateway;

import com.fooddelivery.apigateway.config.AudienceValidator;
import com.fooddelivery.apigateway.config.GatewayJwtAuthenticationConverter;
import com.fooddelivery.apigateway.filter.JwtIdentityHeaderFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.security.jwt.jwk-set-uri=http://localhost:65535/.well-known/jwks.json",
        "app.security.jwt.issuer=food-delivery-auth",
        "app.security.jwt.audience=food-delivery-api",
        "eureka.client.enabled=false",
        "spring.cloud.bus.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.config.import="
})
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void gatewayConverterMapsRolesAndScopes() {
        Jwt jwt = jwt(List.of("food-delivery-api"));

        var authentication = new GatewayJwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_CUSTOMER", "SCOPE_order:create");
    }

    @Test
    void audienceValidatorRejectsWrongAudience() {
        var validator = new AudienceValidator("food-delivery-api");

        assertThat(validator.validate(jwt(List.of("other-api"))).hasErrors()).isTrue();
        assertThat(validator.validate(jwt(List.of("food-delivery-api"))).hasErrors()).isFalse();
    }

    @Test
    void gatewaySecretIsRequiredAndInjectedForUnauthenticatedRequests() {
        assertThatThrownBy(() -> new JwtIdentityHeaderFilter(" "))
                .isInstanceOf(IllegalStateException.class);

        var filter = new JwtIdentityHeaderFilter("trusted-gateway-secret");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/public")
                .header("X-Internal-Gateway-Secret", "forged")
                .build());
        AtomicReference<org.springframework.web.server.ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = filtered -> {
            forwarded.set(filtered);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Internal-Gateway-Secret"))
                .isEqualTo("trusted-gateway-secret");
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
    }

    private Jwt jwt(List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .issuer("food-delivery-auth")
                .audience(audience)
                .claim("email", "customer@example.com")
                .claim("roles", List.of("CUSTOMER"))
                .claim("scope", "order:create")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
    }
}

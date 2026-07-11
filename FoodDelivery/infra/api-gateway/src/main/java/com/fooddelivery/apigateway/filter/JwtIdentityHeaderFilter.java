package com.fooddelivery.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Collection;
import java.util.Map;

@Component
public class JwtIdentityHeaderFilter implements GlobalFilter, Ordered {

    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLE = "X-User-Role";
    private static final String USER_EMAIL = "X-User-Email";
    private static final String SESSION_ID = "X-Session-Id";
    private static final String GATEWAY_SECRET = "X-Internal-Gateway-Secret";

    private final String gatewaySecret;

    public JwtIdentityHeaderFilter(@Value("${app.internal.gateway-secret}") String gatewaySecret) {
        if (!StringUtils.hasText(gatewaySecret)) {
            throw new IllegalStateException("app.internal.gateway-secret must not be blank");
        }
        this.gatewaySecret = gatewaySecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange gatewayExchange = withGatewaySecret(stripIdentityHeaders(exchange));
        return gatewayExchange.getPrincipal()
                .cast(Principal.class)
                .map(principal -> withJwtHeaders(gatewayExchange, principal))
                .defaultIfEmpty(gatewayExchange)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID);
                    headers.remove(USER_ROLE);
                    headers.remove(USER_EMAIL);
                    headers.remove(SESSION_ID);
                    headers.remove(GATEWAY_SECRET);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange withJwtHeaders(ServerWebExchange exchange, Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken token)) {
            return exchange;
        }

        Map<String, Object> claims = token.getTokenAttributes();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(USER_ID, token.getName())
                .headers(headers -> {
                    putIfPresent(headers, USER_ROLE, primaryRole(claims));
                    putIfPresent(headers, USER_EMAIL, claims.get("email"));
                    putIfPresent(headers, SESSION_ID, claims.get("sid"));
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange withGatewaySecret(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(GATEWAY_SECRET, gatewaySecret)
                .build();
        return exchange.mutate().request(request).build();
    }

    private void putIfPresent(org.springframework.http.HttpHeaders headers, String name, Object value) {
        if (value != null) {
            headers.set(name, value.toString());
        }
    }

    private Object primaryRole(Map<String, Object> claims) {
        Object roles = claims.get("roles");
        if (roles instanceof Collection<?> collection && !collection.isEmpty()) {
            return collection.iterator().next();
        }
        return claims.get("role");
    }
}

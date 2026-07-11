package com.fooddelivery.customer.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@ConditionalOnProperty(name = "app.internal.gateway-filter-enabled", havingValue = "true")
public class GatewayInternalSecretFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Internal-Gateway-Secret";

    private final String gatewaySecret;

    public GatewayInternalSecretFilter(@Value("${app.internal.gateway-secret}") String gatewaySecret) {
        if (!StringUtils.hasText(gatewaySecret)) {
            throw new IllegalStateException("app.internal.gateway-secret is required");
        }
        this.gatewaySecret = gatewaySecret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/customers") || path.startsWith("/customers"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!matches(request.getHeader(HEADER_NAME))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                gatewaySecret.getBytes(StandardCharsets.UTF_8));
    }
}

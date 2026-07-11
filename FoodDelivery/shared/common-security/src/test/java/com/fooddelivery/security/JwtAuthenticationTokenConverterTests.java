package com.fooddelivery.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationTokenConverterTests {

    @Test
    void mapsRolesAndScopesToAuthorities() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-id")
                .claim("roles", List.of("CUSTOMER"))
                .claim("scope", "order:create order:read")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();

        var authentication = new FoodDeliveryJwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_CUSTOMER", "SCOPE_order:create", "SCOPE_order:read");
    }

    @Test
    void internalEndpointRejectsMissingSecret() throws Exception {
        var filter = new com.fooddelivery.security.InternalServiceAuthenticationFilter("secret");
        var request = new MockHttpServletRequest("POST", "/internal/v1/payments");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void internalEndpointAuthenticatesServiceCredential() throws Exception {
        var filter = new com.fooddelivery.security.InternalServiceAuthenticationFilter("secret");
        var request = new MockHttpServletRequest("POST", "/internal/v1/payments");
        request.addHeader("X-Internal-Service-Secret", "secret");
        var response = new MockHttpServletResponse();
        try {
            filter.doFilter(request, response, new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_SERVICE");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

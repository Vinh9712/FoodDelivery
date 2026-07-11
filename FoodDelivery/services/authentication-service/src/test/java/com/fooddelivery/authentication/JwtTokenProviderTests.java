package com.fooddelivery.authentication;

import com.fooddelivery.authentication.api.controller.JwkSetController;
import com.fooddelivery.authentication.config.JwtKeyProvider;
import com.fooddelivery.authentication.config.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTests {

    @Test
    void generatesAndValidatesRs256TokenWithRequiredClaims() {
        JwtKeyProvider keys = new JwtKeyProvider("", "", "test-key");
        JwtTokenProvider provider = new JwtTokenProvider(
                keys, "food-delivery-auth", "food-delivery-api", 900_000L);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        String token = provider.generateAccessToken(
                userId, "customer@example.com", "CUSTOMER", sessionId);

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUserIdFromToken(token)).isEqualTo(userId);
        assertThat(provider.getSessionIdFromToken(token)).isEqualTo(sessionId);
        assertThat(provider.getClaimsFromToken(token).getAudience())
                .contains("food-delivery-api");
        assertThat(provider.getClaimsFromToken(token).get("roles", List.class))
                .containsExactly("CUSTOMER");
    }

    @Test
    void rejectsWrongIssuerAudienceAndSigningKey() {
        JwtKeyProvider signingKeys = new JwtKeyProvider("", "", "signing-key");
        JwtTokenProvider issuer = new JwtTokenProvider(
                signingKeys, "food-delivery-auth", "food-delivery-api", 900_000L);
        String token = issuer.generateAccessToken(
                UUID.randomUUID(), "customer@example.com", "CUSTOMER", UUID.randomUUID());

        assertThat(new JwtTokenProvider(
                signingKeys, "other-issuer", "food-delivery-api", 900_000L).validateToken(token)).isFalse();
        assertThat(new JwtTokenProvider(
                signingKeys, "food-delivery-auth", "other-api", 900_000L).validateToken(token)).isFalse();
        assertThat(new JwtTokenProvider(
                new JwtKeyProvider("", "", "other-key"),
                "food-delivery-auth", "food-delivery-api", 900_000L).validateToken(token)).isFalse();
    }

    @Test
    void publishesMatchingRsaJwk() {
        JwtKeyProvider keys = new JwtKeyProvider("", "", "test-key");

        Map<String, Object> response = new JwkSetController(keys).jwks();
        Map<?, ?> jwk = (Map<?, ?>) ((List<?>) response.get("keys")).getFirst();

        assertThat(jwk.get("kid")).isEqualTo("test-key");
        assertThat(jwk.get("alg")).isEqualTo("RS256");
        assertThat(jwk.get("n")).isNotNull();
        assertThat(jwk.get("e")).isNotNull();
    }
}

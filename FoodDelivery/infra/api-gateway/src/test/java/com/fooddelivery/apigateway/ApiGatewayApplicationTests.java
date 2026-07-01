package com.fooddelivery.apigateway;

import com.fooddelivery.apigateway.config.SecurityConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "app.jwt.secret=135791234567892468helloworldantisercet135791234567892468helloworldantisercet",
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
    void gatewayDecoderAcceptsAuthenticationServiceHmacToken() throws Exception {
        String secret = "135791234567892468helloworldantisercet135791234567892468helloworldantisercet";
        UUID userId = UUID.randomUUID();
        String token = hmacToken(secret, userId);
        ReactiveJwtDecoder decoder = new SecurityConfig().jwtDecoder(secret);

        var jwt = decoder.decode(token).block();

        assertEquals(userId.toString(), jwt.getSubject());
        assertEquals("customer@example.com", jwt.getClaimAsString("email"));
        assertEquals("CUSTOMER", jwt.getClaimAsString("role"));
    }

    @Test
    void gatewayDecoderRejectsTokenSignedWithDifferentSecret() throws Exception {
        String gatewaySecret = "135791234567892468helloworldantisercet135791234567892468helloworldantisercet";
        String attackerSecret = "246801234567892468helloworldantisercet246801234567892468helloworldantisercet";
        String token = hmacToken(attackerSecret, UUID.randomUUID());
        ReactiveJwtDecoder decoder = new SecurityConfig().jwtDecoder(gatewaySecret);

        assertThrows(RuntimeException.class, () -> decoder.decode(token).block());
    }

    private String hmacToken(String secret, UUID userId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .claim("email", "customer@example.com")
                .claim("role", "CUSTOMER")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }

}

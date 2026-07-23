package com.fooddelivery.authentication.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import com.github.f4b6a3.uuid.UuidCreator;

@Component
@RefreshScope
public class JwtTokenProvider {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;
    private final String issuer;
    private final String audience;
    @Getter
    private final long expirationMs;

    public JwtTokenProvider(
            JwtKeyProvider keyProvider,
            @Value("${app.jwt.issuer:food-delivery-auth}") String issuer,
            @Value("${app.jwt.audience:food-delivery-api}") String audience,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.privateKey = keyProvider.privateKey();
        this.publicKey = keyProvider.publicKey();
        this.keyId = keyProvider.keyId();
        this.issuer = issuer;
        this.audience = audience;
        this.expirationMs = expirationMs;
    }

    public String generateAccessToken(UUID userId, String email, String role, UUID sessionId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);
        var builder = Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("jti", UuidCreator.getTimeOrderedEpoch().toString())
                .claim("email", email)
                .claim("role", role)
                .claim("roles", List.of(role))
                .claim("scope", scopesFor(role))
                .audience().add(audience).and()
                .issuedAt(now)
                .expiration(expiryDate);
        if (sessionId != null) {
            builder.claim("sid", sessionId.toString());
        }
        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            validateIssuerAndAudience(claims);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public Claims getClaimsFromToken(String token) {
        Claims claims = parseClaims(token);
        validateIssuerAndAudience(claims);
        return claims;
    }

    public UUID getUserIdFromToken(String token) {
        String sub = getClaimsFromToken(token).getSubject();
        return UUID.fromString(sub);
    }

    public String getEmailFromToken(String token) {
        return getClaimsFromToken(token).get("email", String.class);
    }

    public String getRoleFromToken(String token) {
        return getClaimsFromToken(token).get("role", String.class);
    }

    public UUID getSessionIdFromToken(String token) {
        String sid = getClaimsFromToken(token).get("sid", String.class);
        return sid == null ? null : UUID.fromString(sid);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void validateIssuerAndAudience(Claims claims) {
        if (!issuer.equals(claims.getIssuer())) {
            throw new IllegalArgumentException("Invalid token issuer");
        }
        Object aud = claims.get("aud");
        boolean validAudience = aud instanceof Collection<?> values
                ? values.stream().map(Object::toString).anyMatch(audience::equals)
                : audience.equals(String.valueOf(aud));
        if (!validAudience) {
            throw new IllegalArgumentException("Invalid token audience");
        }
    }

    private String scopesFor(String role) {
        return switch (role) {
            case "CUSTOMER" -> "customer:read customer:write order:create order:read restaurant:read review:create";
            case "RESTAURANT_OWNER" -> "restaurant:read restaurant:write menu:write order:read";
            case "DRIVER" -> "delivery:read delivery:write order:read";
            case "ADMIN" -> "admin:write customer:read restaurant:write order:read delivery:write payment:write";
            default -> "";
        };
    }
}

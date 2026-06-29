package com.fooddelivery.authentication.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import com.github.f4b6a3.uuid.UuidCreator;

@Component
@RefreshScope
public class JwtTokenProvider {

    private final SecretKey key;
    @Getter
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateAccessToken(UUID userId, String email, String role, UUID sessionId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("jti", UuidCreator.getTimeOrderedEpoch().toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate);
        if (sessionId != null) {
            builder.claim("sid", sessionId.toString());
        }
        return builder.signWith(key).compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
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
}

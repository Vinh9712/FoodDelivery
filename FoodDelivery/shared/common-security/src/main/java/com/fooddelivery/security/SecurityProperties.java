package com.fooddelivery.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("app.security")
public class SecurityProperties {

    private List<String> publicPaths = new ArrayList<>(List.of(
            "/actuator/health/**",
            "/actuator/info"
    ));
    private List<String> publicGetPaths = new ArrayList<>();
    private String internalServiceSecret = "";
    private final Jwt jwt = new Jwt();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<String> getPublicGetPaths() {
        return publicGetPaths;
    }

    public void setPublicGetPaths(List<String> publicGetPaths) {
        this.publicGetPaths = publicGetPaths;
    }

    public String getInternalServiceSecret() {
        return internalServiceSecret;
    }

    public void setInternalServiceSecret(String internalServiceSecret) {
        this.internalServiceSecret = internalServiceSecret;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public static class Jwt {
        private String jwkSetUri = "http://localhost:8087/.well-known/jwks.json";
        private String issuer = "food-delivery-auth";
        private String audience = "food-delivery-api";

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }
    }
}

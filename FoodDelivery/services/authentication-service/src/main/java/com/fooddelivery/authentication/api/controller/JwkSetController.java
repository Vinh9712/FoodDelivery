package com.fooddelivery.authentication.api.controller;

import com.fooddelivery.authentication.config.JwtKeyProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
public class JwkSetController {

    private final JwtKeyProvider keyProvider;

    public JwkSetController(JwtKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        var publicKey = keyProvider.publicKey();
        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", keyProvider.keyId(),
                "n", base64Url(publicKey.getModulus()),
                "e", base64Url(publicKey.getPublicExponent())
        );
        return Map.of("keys", List.of(jwk));
    }

    private String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

package com.fooddelivery.authentication.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    public JwtKeyProvider(
            @Value("${app.jwt.private-key-base64:}") String privateKeyBase64,
            @Value("${app.jwt.public-key-base64:}") String publicKeyBase64,
            @Value("${app.jwt.key-id:food-delivery-auth-1}") String keyId) {
        this.keyId = keyId;
        if (StringUtils.hasText(privateKeyBase64) && StringUtils.hasText(publicKeyBase64)) {
            KeyPair keyPair = parseKeyPair(privateKeyBase64, publicKeyBase64);
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
            validateKeyPair();
            return;
        }
        if (StringUtils.hasText(privateKeyBase64) || StringUtils.hasText(publicKeyBase64)) {
            throw new IllegalStateException("Both JWT private and public keys must be configured");
        }

        KeyPair keyPair = generateKeyPair();
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        validateKeyPair();
        log.warn("JWT signing keys are ephemeral. Configure app.jwt.private-key-base64 and app.jwt.public-key-base64 for persistent deployments.");
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    private KeyPair parseKeyPair(String privateKeyBase64, String publicKeyBase64) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            var privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(decode(privateKeyBase64)));
            var publicKey = factory.generatePublic(new X509EncodedKeySpec(decode(publicKeyBase64)));
            return new KeyPair(publicKey, privateKey);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA JWT key material", ex);
        }
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate RSA JWT key pair", ex);
        }
    }

    private byte[] decode(String value) {
        return Base64.getDecoder().decode(value.replaceAll("\\s", ""));
    }

    private void validateKeyPair() {
        if (publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalStateException("JWT RSA key must be at least 2048 bits");
        }
        try {
            byte[] challenge = "food-delivery-jwt-key-check".getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(challenge);

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(challenge);
            if (!verifier.verify(signer.sign())) {
                throw new IllegalStateException("JWT private and public keys do not match");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to validate RSA JWT key pair", ex);
        }
    }
}

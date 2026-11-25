package com.campusquest.security;

import com.campusquest.config.SecuritySettings;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * RSA key pair used to sign access tokens (RS256). Keys come from PEM config. Ephemeral keys are only allowed
 * when explicitly enabled (local dev / tests). Production fails fast instead of silently minting keys that
 * would not survive a restart or a second instance.
 */
@Component
public class JwtKeys {

    private static final Logger log = LoggerFactory.getLogger(JwtKeys.class);
    private static final int MIN_RSA_BITS = 2048;

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final String keyId;

    public JwtKeys(SecuritySettings settings) {
        SecuritySettings.Jwt jwt = settings.jwt();
        if (StringUtils.hasText(jwt.privateKey()) && StringUtils.hasText(jwt.publicKey())) {
            this.privateKey = (RSAPrivateKey) parse(jwt.privateKey(), true);
            this.publicKey = (RSAPublicKey) parse(jwt.publicKey(), false);
        } else if (jwt.allowEphemeralKeys()) {
            KeyPair pair = generate();
            this.privateKey = (RSAPrivateKey) pair.getPrivate();
            this.publicKey = (RSAPublicKey) pair.getPublic();
            log.warn("Using an ephemeral JWT signing key. Tokens will not survive a restart. Never do this in production.");
        } else {
            throw new IllegalStateException("JWT signing keys are not configured. Set JWT_PRIVATE_KEY and "
                    + "JWT_PUBLIC_KEY (PEM), or enable campusquest.security.jwt.allow-ephemeral-keys for local development.");
        }
        if (publicKey.getModulus().bitLength() < MIN_RSA_BITS) {
            throw new IllegalStateException("JWT RSA key must be at least " + MIN_RSA_BITS + " bits");
        }
        this.keyId = thumbprint(publicKey);
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public String keyId() {
        return keyId;
    }

    private static java.security.Key parse(String pem, boolean isPrivate) {
        String base64 = Arrays.stream(pem.replace("\\n", "\n").split("\n"))
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("-----"))
                .reduce("", String::concat);
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return isPrivate
                    ? factory.generatePrivate(new PKCS8EncodedKeySpec(der))
                    : factory.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid " + (isPrivate ? "private" : "public")
                    + " JWT key: expected PEM (PKCS#8 private / X.509 public)", e);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String thumbprint(RSAPublicKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "JwtKeys[kid=" + keyId + "]";
    }
}

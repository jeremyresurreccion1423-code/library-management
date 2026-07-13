package com.smartlibrary.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Signs and verifies short-lived Super Admin SSO handoff tokens so a Super Admin
 * authenticated on this system can be transparently signed into the Attendance
 * Management System's Super Admin portal (and vice versa) without re-entering
 * credentials. Both systems must be configured with the same
 * {@code super-admin.sso-secret} value.
 */
@Component
public class SsoTokenService {

    private static final long TOKEN_VALIDITY_MS = 60_000L;

    @Value("${super-admin.sso-secret}")
    private String secret;

    public String generateToken(String username) {
        long expiresAt = System.currentTimeMillis() + TOKEN_VALIDITY_MS;
        String payload = username + "|" + expiresAt;
        String raw = payload + "|" + sign(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<String> validateToken(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|");
            if (parts.length != 3) {
                return Optional.empty();
            }
            String username = parts[0];
            long expiresAt = Long.parseLong(parts[1]);
            String signature = parts[2];
            if (!sign(username + "|" + expiresAt).equals(signature)) {
                return Optional.empty();
            }
            if (System.currentTimeMillis() > expiresAt) {
                return Optional.empty();
            }
            return Optional.of(username);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign SSO token", ex);
        }
    }
}

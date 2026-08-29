package com.company.iss.auth.service;

import com.company.iss.auth.config.AccountSecurityProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class PasswordResetTokenService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final AccountSecurityProperties properties;
    private final SecureRandom secureRandom;

    public PasswordResetTokenService(AccountSecurityProperties properties, SecureRandom secureRandom) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    public void requireConfigured() {
        signingKey();
        trustedBaseUrl();
    }

    public String newPublicRequestId() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    public String tokenFor(String publicRequestId) {
        return publicRequestId + "." + URL_ENCODER.encodeToString(hmac(publicRequestId));
    }

    public String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public Optional<String> verifiedPublicRequestId(String token) {
        if (token == null || token.length() > 128) {
            return Optional.empty();
        }
        int separator = token.indexOf('.');
        if (separator != 22 || separator != token.lastIndexOf('.')) {
            return Optional.empty();
        }
        String publicId = token.substring(0, separator);
        String signature = token.substring(separator + 1);
        try {
            byte[] supplied = Base64.getUrlDecoder().decode(signature);
            if (MessageDigest.isEqual(hmac(publicId), supplied)) {
                return Optional.of(publicId);
            }
        } catch (IllegalArgumentException ignored) {
            // All malformed token forms intentionally share the same public failure.
        }
        return Optional.empty();
    }

    public boolean hashMatches(String token, String expectedHash) {
        return MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    public String resetLink(String token) {
        return trustedBaseUrl() + "/reset-password?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey(), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
        }
    }

    private byte[] signingKey() {
        String configured = properties.getPasswordReset().getSigningSecret();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Password reset signing is not configured.");
        }
        try {
            byte[] key = Base64.getDecoder().decode(configured);
            if (key.length < 32) {
                throw new IllegalStateException("Password reset signing is not configured.");
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Password reset signing is not configured.");
        }
    }

    private String trustedBaseUrl() {
        String configured = properties.getPasswordReset().getPublicBaseUrl();
        try {
            URI uri = URI.create(configured == null ? "" : configured.trim());
            boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            boolean originPath = uri.getPath() == null || uri.getPath().isBlank() || "/".equals(uri.getPath());
            if (uri.getHost() == null
                    || (!("https".equalsIgnoreCase(uri.getScheme()) || localHttp))
                    || !originPath || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException("Password reset public URL is not configured.");
            }
            String normalized = uri.toString();
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Password reset public URL is not configured.");
        }
    }
}

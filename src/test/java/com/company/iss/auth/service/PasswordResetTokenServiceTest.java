package com.company.iss.auth.service;

import com.company.iss.auth.config.AccountSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordResetTokenServiceTest {

    private PasswordResetTokenService service;

    @BeforeEach
    void setUp() {
        AccountSecurityProperties properties = new AccountSecurityProperties();
        properties.getPasswordReset().setPublicBaseUrl("https://iss.example.test");
        properties.getPasswordReset().setSigningSecret(Base64.getEncoder().encodeToString(new byte[32]));
        service = new PasswordResetTokenService(properties, new SecureRandom());
    }

    @Test
    void createsDeterministicSignedTokenFromRandomPublicIdentifier() {
        String publicId = service.newPublicRequestId();
        String token = service.tokenFor(publicId);

        assertEquals(22, publicId.length());
        assertEquals(publicId, service.verifiedPublicRequestId(token).orElseThrow());
        assertTrue(service.hashMatches(token, service.hash(token)));
        assertTrue(service.resetLink(token).startsWith("https://iss.example.test/reset-password?token="));
    }

    @Test
    void rejectsMalformedOrModifiedSignatureUniformly() {
        String token = service.tokenFor(service.newPublicRequestId());
        assertFalse(service.verifiedPublicRequestId(null).isPresent());
        assertFalse(service.verifiedPublicRequestId("malformed").isPresent());
        assertFalse(service.verifiedPublicRequestId(token + "x").isPresent());
    }

    @Test
    void rejectsWeakSigningSecretAndUntrustedPublicUrl() {
        AccountSecurityProperties properties = new AccountSecurityProperties();
        properties.getPasswordReset().setPublicBaseUrl("http://external.example.test");
        properties.getPasswordReset().setSigningSecret(Base64.getEncoder().encodeToString(new byte[16]));
        PasswordResetTokenService invalid = new PasswordResetTokenService(properties, new SecureRandom());

        assertThrows(IllegalStateException.class, invalid::requireConfigured);
    }
}

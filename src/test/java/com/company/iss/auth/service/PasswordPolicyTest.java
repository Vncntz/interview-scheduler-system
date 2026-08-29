package com.company.iss.auth.service;

import com.company.iss.auth.config.AccountSecurityProperties;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private PasswordPolicy policy;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder();
        policy = new PasswordPolicy(new AccountSecurityProperties(), encoder);
    }

    @Test
    void acceptsLongPrintablePasswordWithoutCompositionRules() {
        assertDoesNotThrow(() -> policy.validate("a printable passphrase", "a printable passphrase"));
    }

    @Test
    void rejectsLengthMismatchBlankNullCharacterAndBcryptByteOverflow() {
        assertThrows(BusinessRuleViolationException.class, () -> policy.validate("too short", "too short"));
        assertThrows(BusinessRuleViolationException.class, () -> policy.validate("               ", "               "));
        assertThrows(BusinessRuleViolationException.class, () -> policy.validate("valid length but\0bad", "valid length but\0bad"));
        assertThrows(BusinessRuleViolationException.class, () -> policy.validate("valid printable passphrase", "different confirmation"));
        String utf8Overflow = "界".repeat(25);
        assertThrows(BusinessRuleViolationException.class, () -> policy.validate(utf8Overflow, utf8Overflow));
    }

    @Test
    void rejectsReusingCurrentPassword() {
        String password = "existing secure password";
        assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.rejectCurrentPassword(password, encoder.encode(password))
        );
    }
}

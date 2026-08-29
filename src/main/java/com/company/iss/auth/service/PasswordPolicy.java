package com.company.iss.auth.service;

import com.company.iss.auth.config.AccountSecurityProperties;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class PasswordPolicy {

    private final AccountSecurityProperties properties;
    private final PasswordEncoder passwordEncoder;

    public PasswordPolicy(AccountSecurityProperties properties, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    public void validate(String password, String confirmation) {
        if (password == null || password.isBlank() || password.indexOf('\0') >= 0) {
            throw new BusinessRuleViolationException("Password is required and cannot contain a null character.");
        }
        int characters = password.codePointCount(0, password.length());
        if (characters < properties.getPassword().getMinLength()
                || characters > properties.getPassword().getMaxLength()) {
            throw new BusinessRuleViolationException("Password must be between "
                    + properties.getPassword().getMinLength() + " and "
                    + properties.getPassword().getMaxLength() + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > properties.getPassword().getMaxUtf8Bytes()) {
            throw new BusinessRuleViolationException("Password exceeds the BCrypt 72-byte limit.");
        }
        if (!password.equals(confirmation)) {
            throw new BusinessRuleViolationException("Password confirmation does not match.");
        }
    }

    public void rejectCurrentPassword(String password, String currentHash) {
        if (passwordEncoder.matches(password, currentHash)) {
            throw new BusinessRuleViolationException("The new password must be different from the current password.");
        }
    }
}

package com.company.iss.auth.exception;

import com.company.iss.shared.exception.BusinessRuleViolationException;

public class InvalidPasswordResetTokenException extends BusinessRuleViolationException {

    public InvalidPasswordResetTokenException() {
        super("This password reset link is invalid or has expired.");
    }
}

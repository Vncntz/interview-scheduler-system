package com.company.iss.notification.service;

import com.company.iss.shared.exception.BusinessRuleViolationException;

public class SmtpConfigurationException extends BusinessRuleViolationException {

    private final SmtpConfigurationValidator.Failure failure;

    public SmtpConfigurationException(
            SmtpConfigurationValidator.Failure failure,
            String message
    ) {
        super(message);
        this.failure = failure;
    }

    public SmtpConfigurationValidator.Failure getFailure() {
        return failure;
    }
}

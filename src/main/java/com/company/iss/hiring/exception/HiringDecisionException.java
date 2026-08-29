package com.company.iss.hiring.exception;

import com.company.iss.shared.exception.BusinessRuleViolationException;

public class HiringDecisionException extends BusinessRuleViolationException {

    public HiringDecisionException(String message) {
        super(message);
    }
}

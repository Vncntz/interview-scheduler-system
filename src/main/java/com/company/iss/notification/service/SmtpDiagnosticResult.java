package com.company.iss.notification.service;

public record SmtpDiagnosticResult(Code code, String message) {

    public enum Code {
        CONNECTED,
        TEST_EMAIL_SENT,
        CONFIGURATION_INCOMPLETE,
        INVALID_RECIPIENT,
        AUTHENTICATION_FAILED,
        CONNECTION_FAILED,
        TLS_FAILED,
        SEND_FAILED
    }

    public boolean success() {
        return code == Code.CONNECTED || code == Code.TEST_EMAIL_SENT;
    }
}

package com.company.iss.notification.config;

public enum SmtpProvider {
    GMAIL("Gmail"),
    MICROSOFT_365("Microsoft 365 / Outlook"),
    CUSTOM("Custom SMTP");

    private final String displayName;

    SmtpProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

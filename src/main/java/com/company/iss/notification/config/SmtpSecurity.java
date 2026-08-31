package com.company.iss.notification.config;

public enum SmtpSecurity {
    STARTTLS("STARTTLS"),
    SSL("SSL/TLS");

    private final String displayName;

    SmtpSecurity(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

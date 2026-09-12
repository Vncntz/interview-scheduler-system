package com.company.iss.notification.dto;

public enum ReminderDeliveryHealthIndicator {
    RETRY_SCHEDULED("Retry scheduled"),
    ATTEMPTS_EXHAUSTED("Attempts exhausted"),
    STALE_PENDING_CLAIM("Stale pending claim"),
    EXPIRED_REMINDER_WINDOW("Expired reminder window"),
    OBSOLETE_GENERATION("Obsolete after rescheduling");

    private final String displayName;

    ReminderDeliveryHealthIndicator(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

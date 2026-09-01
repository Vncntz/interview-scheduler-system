package com.company.iss.notification.service;

public record ReminderNotificationResult(Disposition disposition, String reason, String detailCode) {

    public enum Disposition {
        SENT,
        RETRYABLE_FAILURE,
        SKIPPED
    }

    public static ReminderNotificationResult sent() {
        return new ReminderNotificationResult(Disposition.SENT, "SENT", "SENT");
    }

    public static ReminderNotificationResult retryable(String reason) {
        return retryable(reason, reason);
    }

    public static ReminderNotificationResult retryable(String reason, String detailCode) {
        return new ReminderNotificationResult(Disposition.RETRYABLE_FAILURE, reason, detailCode);
    }

    public static ReminderNotificationResult skipped(String reason) {
        return new ReminderNotificationResult(Disposition.SKIPPED, reason, reason);
    }
}

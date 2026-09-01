package com.company.iss.notification.service;

import com.company.iss.notification.dto.InterviewReminderContext;

public record InterviewReminderClaimResult(
        Disposition disposition,
        InterviewReminderContext context
) {

    public enum Disposition {
        CLAIMED,
        SKIPPED,
        DUPLICATE
    }

    public static InterviewReminderClaimResult claimed(InterviewReminderContext context) {
        return new InterviewReminderClaimResult(Disposition.CLAIMED, context);
    }

    public static InterviewReminderClaimResult skipped() {
        return new InterviewReminderClaimResult(Disposition.SKIPPED, null);
    }

    public static InterviewReminderClaimResult duplicate() {
        return new InterviewReminderClaimResult(Disposition.DUPLICATE, null);
    }
}

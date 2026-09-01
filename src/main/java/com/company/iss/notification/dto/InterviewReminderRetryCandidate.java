package com.company.iss.notification.dto;

import com.company.iss.notification.entity.InterviewReminderType;

public record InterviewReminderRetryCandidate(
        Long deliveryId,
        Long bookingId,
        InterviewReminderType reminderType
) {
}

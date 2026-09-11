package com.company.iss.notification.dto;

import com.company.iss.notification.entity.InterviewReminderDeliveryStatus;
import com.company.iss.notification.entity.InterviewReminderType;

import java.time.LocalDateTime;
import java.util.Set;

public record ReminderDeliveryHealthItem(
        Long deliveryId,
        String bookingReference,
        InterviewReminderType reminderType,
        LocalDateTime scheduledStartAt,
        InterviewReminderDeliveryStatus persistedStatus,
        int attemptCount,
        LocalDateTime claimedAtUtc,
        LocalDateTime nextAttemptAtUtc,
        LocalDateTime sentAtUtc,
        String safeStatusReason,
        Set<ReminderDeliveryHealthIndicator> healthIndicators
) {

    public ReminderDeliveryHealthItem {
        healthIndicators = Set.copyOf(healthIndicators);
    }
}

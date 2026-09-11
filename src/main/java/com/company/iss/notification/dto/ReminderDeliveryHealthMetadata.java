package com.company.iss.notification.dto;

import java.time.ZoneId;

public record ReminderDeliveryHealthMetadata(
        ZoneId businessZone,
        boolean reminderSchedulerEnabled,
        int maxAttempts
) {
}

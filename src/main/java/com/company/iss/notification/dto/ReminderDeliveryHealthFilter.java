package com.company.iss.notification.dto;

import com.company.iss.notification.entity.InterviewReminderDeliveryStatus;
import com.company.iss.notification.entity.InterviewReminderType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReminderDeliveryHealthFilter(
        InterviewReminderDeliveryStatus status,
        InterviewReminderType reminderType,
        LocalDate appointmentDateFrom,
        LocalDate appointmentDateThrough
) {

    public ReminderDeliveryHealthFilter {
        if (appointmentDateFrom != null
                && appointmentDateThrough != null
                && appointmentDateFrom.isAfter(appointmentDateThrough)) {
            throw new IllegalArgumentException("Appointment date from must not be after appointment date through.");
        }
    }

    public static ReminderDeliveryHealthFilter empty() {
        return new ReminderDeliveryHealthFilter(null, null, null, null);
    }

    public LocalDateTime scheduledFromInclusive() {
        return appointmentDateFrom == null ? null : appointmentDateFrom.atStartOfDay();
    }

    public LocalDateTime scheduledThroughExclusive() {
        return appointmentDateThrough == null ? null : appointmentDateThrough.plusDays(1).atStartOfDay();
    }
}

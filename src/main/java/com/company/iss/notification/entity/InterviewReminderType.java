package com.company.iss.notification.entity;

public enum InterviewReminderType {
    REMINDER_24H(NotificationEvent.INTERVIEW_REMINDER_24H),
    REMINDER_2H(NotificationEvent.INTERVIEW_REMINDER_2H);

    private final NotificationEvent notificationEvent;

    InterviewReminderType(NotificationEvent notificationEvent) {
        this.notificationEvent = notificationEvent;
    }

    public NotificationEvent notificationEvent() {
        return notificationEvent;
    }
}

package com.company.iss.notification.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InterviewReminderScheduler {

    private final InterviewReminderService reminderService;

    public InterviewReminderScheduler(InterviewReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(
            fixedDelayString = "${iss.notification.reminders.fixed-delay:5m}",
            initialDelayString = "${iss.notification.reminders.initial-delay:30s}"
    )
    public void processInterviewReminders() {
        reminderService.processDueReminders();
    }
}

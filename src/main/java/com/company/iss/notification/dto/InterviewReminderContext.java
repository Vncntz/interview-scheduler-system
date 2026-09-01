package com.company.iss.notification.dto;

import com.company.iss.notification.entity.InterviewReminderType;

import java.time.ZonedDateTime;

public record InterviewReminderContext(
        Long deliveryId,
        Long bookingId,
        int reminderGeneration,
        InterviewReminderType reminderType,
        String claimToken,
        String recipientEmail,
        String applicantName,
        String bookingReference,
        ZonedDateTime interviewStart,
        String interviewStage,
        String interviewMode,
        String recruiter,
        String branch,
        String position,
        String client,
        String location
) {
}

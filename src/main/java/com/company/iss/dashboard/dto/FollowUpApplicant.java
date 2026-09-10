package com.company.iss.dashboard.dto;

import com.company.iss.booking.entity.InterviewStage;

import java.time.Duration;
import java.time.LocalDateTime;

public record FollowUpApplicant(
        Long applicantId,
        Long branchId,
        String applicantName,
        String positionTitle,
        String clientName,
        InterviewStage requiredStage,
        LocalDateTime relatedAppointmentAt,
        LocalDateTime waitingSince,
        LocalDateTime dueAt,
        Duration elapsed,
        FollowUpSlaStatus deadlineStatus
) {
}

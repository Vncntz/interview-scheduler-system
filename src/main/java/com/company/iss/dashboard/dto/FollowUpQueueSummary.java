package com.company.iss.dashboard.dto;

import com.company.iss.booking.entity.InterviewStage;

import java.time.Duration;
import java.time.LocalDateTime;

public record FollowUpQueueSummary(
        InterviewStage stage,
        Duration target,
        long total,
        long onTrack,
        long dueSoon,
        long overdue,
        long timingUnavailable,
        LocalDateTime calculatedAt
) {
}

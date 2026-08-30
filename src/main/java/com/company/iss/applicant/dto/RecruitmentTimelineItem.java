package com.company.iss.applicant.dto;

import com.company.iss.booking.entity.InterviewStage;

import java.time.LocalDateTime;

public record RecruitmentTimelineItem(
        RecruitmentTimelineEvent event,
        LocalDateTime occurredAt,
        String title,
        String description,
        InterviewStage interviewStage
) {
}

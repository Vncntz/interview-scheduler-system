package com.company.iss.booking.dto;

import com.company.iss.booking.entity.InterviewStage;

public record CreateBookingCommand(
        Long applicantId,
        Long scheduleId,
        InterviewStage interviewStage,
        String remarks
) {
}

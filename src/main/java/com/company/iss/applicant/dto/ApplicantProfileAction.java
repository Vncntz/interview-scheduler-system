package com.company.iss.applicant.dto;

import com.company.iss.booking.entity.InterviewStage;

public record ApplicantProfileAction(
        ApplicantProfileActionType type,
        String label,
        Long applicantId,
        Long bookingId,
        InterviewStage interviewStage
) {
}

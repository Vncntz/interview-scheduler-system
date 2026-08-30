package com.company.iss.applicant.dto;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.InterviewStage;

public record ApplicantRecruitmentState(
        ApplicantStatus status,
        InterviewStage currentStage,
        InterviewStage nextRequiredStage,
        ApplicantNextAction nextAction,
        ApplicantInterviewSummary currentInterview,
        boolean complete,
        boolean closed
) {
}

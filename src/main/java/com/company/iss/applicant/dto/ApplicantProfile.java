package com.company.iss.applicant.dto;

import java.util.List;

public record ApplicantProfile(
        ApplicantSummary summary,
        ApplicantRecruitmentState currentState,
        List<ApplicantProfileAction> actions,
        List<RecruitmentTimelineItem> timeline
) {
    public ApplicantProfile {
        actions = List.copyOf(actions);
        timeline = List.copyOf(timeline);
    }
}

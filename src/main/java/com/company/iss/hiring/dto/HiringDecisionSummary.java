package com.company.iss.hiring.dto;

import com.company.iss.hiring.entity.HiringDecisionStatus;

import java.time.LocalDateTime;

public record HiringDecisionSummary(
        Long decisionId,
        Long applicantId,
        String applicantName,
        String branch,
        String position,
        String client,
        String workLocation,
        HiringDecisionStatus status,
        String offeredBy,
        LocalDateTime offeredAt,
        String offeredRemarks,
        String resolvedBy,
        LocalDateTime resolvedAt,
        String resolutionRemarks
) {
}

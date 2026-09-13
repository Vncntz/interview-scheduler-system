package com.company.iss.hiring.dto;

import com.company.iss.hiring.entity.HiringDecisionStatus;
import com.company.iss.hiring.entity.OfferDeadlineState;

import java.time.Duration;
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
        LocalDateTime responseDueAt,
        Duration offerAge,
        OfferDeadlineState deadlineState,
        String offeredRemarks,
        String resolvedBy,
        LocalDateTime resolvedAt,
        String resolutionRemarks
) {
}

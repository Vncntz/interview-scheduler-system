package com.company.iss.hiring.dto;

import java.time.LocalDateTime;

public record EligibleHiringCandidate(
        Long applicantId,
        Long evaluationId,
        String applicantName,
        String branch,
        String position,
        String client,
        String workLocation,
        LocalDateTime evaluatedAt
) {
}

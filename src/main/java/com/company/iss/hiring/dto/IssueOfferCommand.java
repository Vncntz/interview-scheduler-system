package com.company.iss.hiring.dto;

import java.time.LocalDateTime;

public record IssueOfferCommand(
        Long applicantId,
        Long evaluationId,
        LocalDateTime responseDueAt,
        String remarks
) {

    public IssueOfferCommand(Long applicantId, Long evaluationId, String remarks) {
        this(applicantId, evaluationId, null, remarks);
    }
}

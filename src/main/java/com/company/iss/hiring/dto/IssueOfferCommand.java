package com.company.iss.hiring.dto;

public record IssueOfferCommand(Long applicantId, Long evaluationId, String remarks) {
}

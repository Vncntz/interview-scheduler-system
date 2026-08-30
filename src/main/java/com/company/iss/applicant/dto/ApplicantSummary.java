package com.company.iss.applicant.dto;

import com.company.iss.applicant.entity.ApplicantStatus;

import java.time.LocalDateTime;

public record ApplicantSummary(
        Long applicantId,
        Long branchId,
        String fullName,
        String email,
        String mobileNumber,
        String branch,
        String position,
        String client,
        String workLocation,
        ApplicantStatus status,
        boolean active,
        String source,
        String remarks,
        LocalDateTime createdAt
) {
}

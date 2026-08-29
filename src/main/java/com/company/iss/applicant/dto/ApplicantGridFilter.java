package com.company.iss.applicant.dto;

import com.company.iss.applicant.entity.ApplicantStatus;

import java.util.Locale;

public record ApplicantGridFilter(String keyword, ApplicantStatus status) {

    public ApplicantGridFilter {
        keyword = normalizeKeyword(keyword);
    }

    public static ApplicantGridFilter empty() {
        return new ApplicantGridFilter(null, null);
    }

    private static String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank()
                ? null
                : keyword.trim().toLowerCase(Locale.ROOT);
    }
}

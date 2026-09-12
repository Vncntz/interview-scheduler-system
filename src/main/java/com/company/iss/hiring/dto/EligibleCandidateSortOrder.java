package com.company.iss.hiring.dto;

import org.springframework.data.domain.Sort;

public record EligibleCandidateSortOrder(EligibleCandidateSort field, Sort.Direction direction) {

    public EligibleCandidateSortOrder {
        if (field == null || direction == null) {
            throw new IllegalArgumentException("Eligible candidate sort field and direction are required.");
        }
    }
}

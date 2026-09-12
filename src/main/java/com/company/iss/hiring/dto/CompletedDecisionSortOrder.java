package com.company.iss.hiring.dto;

import org.springframework.data.domain.Sort;

public record CompletedDecisionSortOrder(CompletedDecisionSort field, Sort.Direction direction) {

    public CompletedDecisionSortOrder {
        if (field == null || direction == null) {
            throw new IllegalArgumentException("Completed decision sort field and direction are required.");
        }
    }
}

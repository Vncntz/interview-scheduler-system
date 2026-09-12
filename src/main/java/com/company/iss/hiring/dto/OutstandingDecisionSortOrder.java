package com.company.iss.hiring.dto;

import org.springframework.data.domain.Sort;

public record OutstandingDecisionSortOrder(OutstandingDecisionSort field, Sort.Direction direction) {

    public OutstandingDecisionSortOrder {
        if (field == null || direction == null) {
            throw new IllegalArgumentException("Outstanding decision sort field and direction are required.");
        }
    }
}

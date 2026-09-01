package com.company.iss.evaluation.dto;

import org.springframework.data.domain.Sort;

import java.util.Objects;

public record EvaluationGridSortOrder(EvaluationGridSort field, Sort.Direction direction) {

    public EvaluationGridSortOrder {
        Objects.requireNonNull(field, "Evaluation sort field is required.");
        Objects.requireNonNull(direction, "Evaluation sort direction is required.");
    }
}

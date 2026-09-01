package com.company.iss.schedule.dto;

import org.springframework.data.domain.Sort;

import java.util.Objects;

public record ScheduleGridSortOrder(ScheduleGridSort field, Sort.Direction direction) {

    public ScheduleGridSortOrder {
        Objects.requireNonNull(field, "Schedule sort field is required.");
        Objects.requireNonNull(direction, "Schedule sort direction is required.");
    }
}

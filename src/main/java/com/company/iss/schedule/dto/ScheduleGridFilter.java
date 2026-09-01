package com.company.iss.schedule.dto;

import java.util.Locale;

public record ScheduleGridFilter(String keyword) {

    public ScheduleGridFilter {
        keyword = normalize(keyword);
    }

    public static ScheduleGridFilter empty() {
        return new ScheduleGridFilter(null);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

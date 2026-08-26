package com.company.iss.dashboard.dto;

import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.ScheduleStatus;

import java.time.LocalTime;

public record ScheduleSummary(
        LocalTime startTime,
        LocalTime endTime,
        int bookedCount,
        int capacity,
        InterviewMode mode,
        ScheduleStatus status,
        String branch
) {
}

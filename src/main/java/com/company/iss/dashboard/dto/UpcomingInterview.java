package com.company.iss.dashboard.dto;

import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.schedule.entity.InterviewMode;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpcomingInterview(
        LocalDate date,
        LocalTime time,
        String position,
        String applicant,
        String recruiter,
        String branch,
        InterviewMode mode,
        BookingStatus status
) {
}

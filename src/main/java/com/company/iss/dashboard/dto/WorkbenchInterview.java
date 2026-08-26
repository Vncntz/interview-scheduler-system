package com.company.iss.dashboard.dto;

import com.company.iss.booking.entity.BookingStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record WorkbenchInterview(
        Long bookingId,
        String bookingReference,
        String applicant,
        String position,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String recruiter,
        BookingStatus status
) {
}

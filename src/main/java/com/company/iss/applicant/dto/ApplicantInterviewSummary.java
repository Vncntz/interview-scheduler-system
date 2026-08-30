package com.company.iss.applicant.dto;

import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.schedule.entity.InterviewMode;

import java.time.LocalDate;
import java.time.LocalTime;

public record ApplicantInterviewSummary(
        Long bookingId,
        String bookingReference,
        InterviewStage interviewStage,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        InterviewMode interviewMode,
        String recruiter,
        BookingStatus bookingStatus
) {
}

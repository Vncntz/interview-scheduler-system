package com.company.iss.dashboard.repository;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public interface FollowUpApplicantProjection {

    Long getApplicantId();

    Long getBranchId();

    String getApplicantName();

    String getPositionTitle();

    String getClientName();

    ApplicantStatus getApplicantStatus();

    InterviewStage getMostRecentBookingStage();

    BookingStatus getMostRecentBookingStatus();

    LocalDateTime getWaitingSince();

    LocalDate getRelatedAppointmentDate();

    LocalTime getRelatedAppointmentStartTime();
}

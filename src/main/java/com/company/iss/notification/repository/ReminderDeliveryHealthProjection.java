package com.company.iss.notification.repository;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.notification.entity.InterviewReminderDeliveryStatus;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.schedule.entity.ScheduleStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public interface ReminderDeliveryHealthProjection {

    Long getDeliveryId();

    String getBookingReference();

    int getReminderGeneration();

    InterviewReminderType getReminderType();

    InterviewReminderDeliveryStatus getDeliveryStatus();

    LocalDateTime getScheduledStartAt();

    int getAttemptCount();

    LocalDateTime getClaimedAt();

    LocalDateTime getNextAttemptAt();

    LocalDateTime getSentAt();

    String getStatusReason();

    int getCurrentReminderGeneration();

    BookingStatus getCurrentBookingStatus();

    Boolean getApplicantActive();

    ApplicantStatus getCurrentApplicantStatus();

    Boolean getScheduleActive();

    ScheduleStatus getCurrentScheduleStatus();

    LocalDate getCurrentScheduleDate();

    LocalTime getCurrentScheduleStartTime();
}

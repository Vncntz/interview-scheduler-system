package com.company.iss.booking.entity;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.auth.entity.User;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "bookings")
@Getter
@Setter
public class Booking extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String bookingReference;

    @ManyToOne
    private Applicant applicant;

    @ManyToOne
    private Schedule schedule;

    @ManyToOne
    private User recruiter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status = BookingStatus.BOOKED;

    @Setter(lombok.AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "interview_stage", nullable = false, length = 20, updatable = false)
    private InterviewStage interviewStage = InterviewStage.INITIAL;

    @Column(length = 1000)
    private String remarks;

    @Column(nullable = false)
    private LocalDateTime bookedDateTime = LocalDateTime.now();

    @Setter(lombok.AccessLevel.NONE)
    @Column(name = "reminder_generation", nullable = false)
    private int reminderGeneration;

    public static Booking forInterviewStage(InterviewStage interviewStage) {
        Booking booking = new Booking();
        booking.interviewStage = Objects.requireNonNull(interviewStage, "Interview stage is required.");
        return booking;
    }

    public void advanceReminderGeneration() {
        reminderGeneration = Math.addExact(reminderGeneration, 1);
    }
}

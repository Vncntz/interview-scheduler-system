package com.company.iss.booking.entity;

import com.company.iss.auth.entity.User;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

@Entity
@Table(
        name = "booking_lifecycle_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booking_lifecycle_action",
                columnNames = {"booking_id", "action"}
        )
)
@Immutable
@Getter
public class BookingLifecycleHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false, updatable = false)
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private BookingLifecycleAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false, length = 30)
    private BookingStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false, length = 30)
    private BookingStatus newStatus;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "booking_reference", nullable = false, updatable = false, length = 50)
    private String bookingReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_stage", nullable = false, updatable = false, length = 20)
    private InterviewStage interviewStage;

    @Column(name = "appointment_date", nullable = false, updatable = false)
    private LocalDate appointmentDate;

    @Column(name = "start_time", nullable = false, updatable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false, updatable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_mode", nullable = false, updatable = false, length = 20)
    private InterviewMode interviewMode;

    @Column(name = "recruiter_display_name", updatable = false)
    private String recruiterDisplayName;

    @Column(name = "branch_display_name", updatable = false)
    private String branchDisplayName;

    protected BookingLifecycleHistory() {
    }

    private BookingLifecycleHistory(
            Booking booking,
            Schedule schedule,
            User actor,
            BookingLifecycleAction action,
            BookingStatus previousStatus,
            BookingStatus newStatus,
            LocalDateTime occurredAt
    ) {
        this.booking = Objects.requireNonNull(booking, "booking is required");
        this.schedule = Objects.requireNonNull(schedule, "schedule is required");
        this.actor = Objects.requireNonNull(actor, "actor is required");
        this.action = Objects.requireNonNull(action, "action is required");
        this.newStatus = Objects.requireNonNull(newStatus, "newStatus is required");
        this.action.validateTransition(previousStatus, this.newStatus);
        this.previousStatus = previousStatus;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        this.bookingReference = Objects.requireNonNull(booking.getBookingReference(), "bookingReference is required");
        this.interviewStage = Objects.requireNonNull(booking.getInterviewStage(), "interviewStage is required");
        this.appointmentDate = Objects.requireNonNull(schedule.getScheduleDate(), "appointmentDate is required");
        this.startTime = Objects.requireNonNull(schedule.getStartTime(), "startTime is required");
        this.endTime = Objects.requireNonNull(schedule.getEndTime(), "endTime is required");
        this.interviewMode = Objects.requireNonNull(schedule.getInterviewMode(), "interviewMode is required");
        this.recruiterDisplayName = schedule.getRecruiter() == null ? null : schedule.getRecruiter().getFullName();
        this.branchDisplayName = schedule.getBranch() == null ? null : schedule.getBranch().getBranchName();
    }

    public static BookingLifecycleHistory record(
            Booking booking,
            Schedule schedule,
            User actor,
            BookingLifecycleAction action,
            BookingStatus previousStatus,
            BookingStatus newStatus,
            LocalDateTime occurredAt
    ) {
        return new BookingLifecycleHistory(
                booking, schedule, actor, action, previousStatus, newStatus, occurredAt
        );
    }
}

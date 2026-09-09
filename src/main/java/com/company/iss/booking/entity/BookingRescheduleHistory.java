package com.company.iss.booking.entity;

import com.company.iss.auth.entity.User;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

@Entity
@Table(name = "booking_reschedule_history")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingRescheduleHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_schedule_id", nullable = false, updatable = false)
    private Schedule sourceSchedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_schedule_id", nullable = false, updatable = false)
    private Schedule destinationSchedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Column(nullable = false, updatable = false)
    private LocalDateTime rescheduledAt;

    @Column(nullable = false, updatable = false, length = 1000)
    private String reason;

    @Column(name = "snapshot_version", updatable = false)
    private Short snapshotVersion;

    @Column(name = "source_appointment_date", updatable = false)
    private LocalDate sourceAppointmentDate;

    @Column(name = "source_start_time", updatable = false)
    private LocalTime sourceStartTime;

    @Column(name = "source_end_time", updatable = false)
    private LocalTime sourceEndTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_interview_mode", updatable = false, length = 20)
    private InterviewMode sourceInterviewMode;

    @Column(name = "source_recruiter_display_name", updatable = false)
    private String sourceRecruiterDisplayName;

    @Column(name = "source_branch_display_name", updatable = false)
    private String sourceBranchDisplayName;

    @Column(name = "destination_appointment_date", updatable = false)
    private LocalDate destinationAppointmentDate;

    @Column(name = "destination_start_time", updatable = false)
    private LocalTime destinationStartTime;

    @Column(name = "destination_end_time", updatable = false)
    private LocalTime destinationEndTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_interview_mode", updatable = false, length = 20)
    private InterviewMode destinationInterviewMode;

    @Column(name = "destination_recruiter_display_name", updatable = false)
    private String destinationRecruiterDisplayName;

    @Column(name = "destination_branch_display_name", updatable = false)
    private String destinationBranchDisplayName;

    private BookingRescheduleHistory(
            Booking booking,
            Schedule sourceSchedule,
            Schedule destinationSchedule,
            User actor,
            LocalDateTime rescheduledAt,
            String reason
    ) {
        this.booking = Objects.requireNonNull(booking, "booking is required");
        this.sourceSchedule = Objects.requireNonNull(sourceSchedule, "sourceSchedule is required");
        this.destinationSchedule = Objects.requireNonNull(destinationSchedule, "destinationSchedule is required");
        this.actor = Objects.requireNonNull(actor, "actor is required");
        this.rescheduledAt = Objects.requireNonNull(rescheduledAt, "rescheduledAt is required");
        this.reason = Objects.requireNonNull(reason, "reason is required");
        this.snapshotVersion = 1;
        this.sourceAppointmentDate = sourceSchedule.getScheduleDate();
        this.sourceStartTime = sourceSchedule.getStartTime();
        this.sourceEndTime = sourceSchedule.getEndTime();
        this.sourceInterviewMode = sourceSchedule.getInterviewMode();
        this.sourceRecruiterDisplayName = displayName(sourceSchedule);
        this.sourceBranchDisplayName = branchName(sourceSchedule);
        this.destinationAppointmentDate = destinationSchedule.getScheduleDate();
        this.destinationStartTime = destinationSchedule.getStartTime();
        this.destinationEndTime = destinationSchedule.getEndTime();
        this.destinationInterviewMode = destinationSchedule.getInterviewMode();
        this.destinationRecruiterDisplayName = displayName(destinationSchedule);
        this.destinationBranchDisplayName = branchName(destinationSchedule);
    }

    public static BookingRescheduleHistory record(
            Booking booking,
            Schedule sourceSchedule,
            Schedule destinationSchedule,
            User actor,
            LocalDateTime rescheduledAt,
            String reason
    ) {
        return new BookingRescheduleHistory(
                booking,
                sourceSchedule,
                destinationSchedule,
                actor,
                rescheduledAt,
                reason
        );
    }

    private String displayName(Schedule schedule) {
        return schedule.getRecruiter() == null ? null : schedule.getRecruiter().getFullName();
    }

    private String branchName(Schedule schedule) {
        return schedule.getBranch() == null ? null : schedule.getBranch().getBranchName();
    }
}

package com.company.iss.booking.entity;

import com.company.iss.auth.entity.User;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

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

    public BookingRescheduleHistory(
            Booking booking,
            Schedule sourceSchedule,
            Schedule destinationSchedule,
            User actor,
            LocalDateTime rescheduledAt,
            String reason
    ) {
        this.booking = booking;
        this.sourceSchedule = sourceSchedule;
        this.destinationSchedule = destinationSchedule;
        this.actor = actor;
        this.rescheduledAt = rescheduledAt;
        this.reason = reason;
    }
}

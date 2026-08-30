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
}

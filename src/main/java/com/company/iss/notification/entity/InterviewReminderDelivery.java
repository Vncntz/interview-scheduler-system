package com.company.iss.notification.entity;

import com.company.iss.booking.entity.Booking;
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

import java.time.LocalDateTime;

@Entity
@Table(
        name = "interview_reminder_deliveries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interview_reminder_booking_generation_type",
                columnNames = {"booking_id", "reminder_generation", "reminder_type"}
        )
)
@Getter
public class InterviewReminderDelivery extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "reminder_generation", nullable = false)
    private int reminderGeneration;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 30)
    private InterviewReminderType reminderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewReminderDeliveryStatus status;

    @Column(name = "scheduled_start_at", nullable = false, updatable = false)
    private LocalDateTime scheduledStartAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "status_reason", length = 80)
    private String statusReason;

    protected InterviewReminderDelivery() {
    }

    public static InterviewReminderDelivery pending(
            Booking booking,
            InterviewReminderType reminderType,
            LocalDateTime scheduledStartAt
    ) {
        InterviewReminderDelivery delivery = new InterviewReminderDelivery();
        delivery.booking = booking;
        delivery.reminderGeneration = booking.getReminderGeneration();
        delivery.reminderType = reminderType;
        delivery.status = InterviewReminderDeliveryStatus.PENDING;
        delivery.scheduledStartAt = scheduledStartAt;
        return delivery;
    }

    public void claim(String token, LocalDateTime now) {
        status = InterviewReminderDeliveryStatus.PENDING;
        claimToken = token;
        claimedAt = now;
        nextAttemptAt = null;
        statusReason = null;
        attemptCount++;
    }

    public void markSent(LocalDateTime now) {
        status = InterviewReminderDeliveryStatus.SENT;
        sentAt = now;
        clearClaim();
    }

    public void markFailed(LocalDateTime nextAttempt, String reason) {
        status = InterviewReminderDeliveryStatus.FAILED;
        nextAttemptAt = nextAttempt;
        statusReason = reason;
        claimToken = null;
        claimedAt = null;
    }

    public boolean hasClaim(String token) {
        return status == InterviewReminderDeliveryStatus.PENDING
                && claimToken != null
                && claimToken.equals(token);
    }

    public void markSkipped(String reason) {
        status = InterviewReminderDeliveryStatus.SKIPPED;
        statusReason = reason;
        clearClaim();
    }

    private void clearClaim() {
        claimToken = null;
        claimedAt = null;
        nextAttemptAt = null;
    }
}

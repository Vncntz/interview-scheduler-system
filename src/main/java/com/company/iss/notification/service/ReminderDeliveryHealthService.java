package com.company.iss.notification.service;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.dto.ReminderDeliveryHealthFilter;
import com.company.iss.notification.dto.ReminderDeliveryHealthIndicator;
import com.company.iss.notification.dto.ReminderDeliveryHealthItem;
import com.company.iss.notification.dto.ReminderDeliveryHealthMetadata;
import com.company.iss.notification.entity.InterviewReminderDeliveryStatus;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.notification.repository.InterviewReminderDeliveryRepository;
import com.company.iss.notification.repository.ReminderDeliveryHealthProjection;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.shared.pagination.OffsetLimitPageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class ReminderDeliveryHealthService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<BookingStatus> ELIGIBLE_BOOKING_STATUSES = EnumSet.of(
            BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED
    );
    private static final Map<String, String> SAFE_REASONS = Map.ofEntries(
            Map.entry("EMAIL_DISABLED", "Email delivery was disabled."),
            Map.entry("TEMPLATE_MISSING", "The reminder template was unavailable."),
            Map.entry("TEMPLATE_DISABLED", "The reminder template was disabled."),
            Map.entry("INVALID_RECIPIENT", "The recipient address was invalid."),
            Map.entry("TEMPLATE_RENDER_FAILED", "The reminder template could not be rendered."),
            Map.entry("SMTP_CONFIGURATION_INVALID", "The SMTP configuration was invalid."),
            Map.entry("SMTP_AUTHENTICATION_FAILED", "SMTP authentication failed."),
            Map.entry("SMTP_CONNECTION_FAILED", "The SMTP server connection failed."),
            Map.entry("SMTP_SEND_FAILED", "The SMTP server did not accept the message."),
            Map.entry("EMAIL_DELIVERY_FAILED", "The email delivery attempt failed."),
            Map.entry("INVALID_MESSAGE", "The reminder message was invalid."),
            Map.entry("MAX_ATTEMPTS_REACHED", "The configured attempt limit was reached."),
            Map.entry("REMINDER_WINDOW_EXPIRED", "The reminder window expired before another attempt."),
            Map.entry("STALE_GENERATION", "The booking was rescheduled after this reminder was created."),
            Map.entry("BOOKING_NOT_ELIGIBLE", "The booking was no longer eligible for a reminder."),
            Map.entry("APPLICANT_NOT_ELIGIBLE", "The applicant was no longer eligible for a reminder."),
            Map.entry("SCHEDULE_NOT_ELIGIBLE", "The schedule was no longer eligible for a reminder."),
            Map.entry("OUTSIDE_REMINDER_WINDOW", "The appointment was outside this reminder window.")
    );
    private static final String UNAVAILABLE_REASON = "Delivery status detail unavailable.";
    private static final String SENT_REASON =
            "SMTP accepted the message; delivery to the recipient's inbox is not confirmed.";

    private final InterviewReminderDeliveryRepository deliveryRepository;
    private final SecurityService securityService;
    private final InterviewReminderTiming timing;
    private final NotificationRuntimeProperties properties;
    private final Clock clock;

    public ReminderDeliveryHealthService(
            InterviewReminderDeliveryRepository deliveryRepository,
            SecurityService securityService,
            InterviewReminderTiming timing,
            NotificationRuntimeProperties properties,
            Clock clock
    ) {
        this.deliveryRepository = deliveryRepository;
        this.securityService = securityService;
        this.timing = timing;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public java.util.List<ReminderDeliveryHealthItem> findPage(
            ReminderDeliveryHealthFilter filter,
            long offset,
            int limit
    ) {
        securityService.requireAdmin();
        ReminderDeliveryHealthFilter safeFilter = requireFilter(filter);
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative.");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        Instant now = clock.instant();
        return deliveryRepository.findHealthPage(
                safeFilter.status(),
                safeFilter.reminderType(),
                safeFilter.scheduledFromInclusive(),
                safeFilter.scheduledThroughExclusive(),
                new OffsetLimitPageable(offset, limit)
        ).stream().map(projection -> toItem(projection, now)).toList();
    }

    @Transactional(readOnly = true)
    public long count(ReminderDeliveryHealthFilter filter) {
        securityService.requireAdmin();
        ReminderDeliveryHealthFilter safeFilter = requireFilter(filter);
        return deliveryRepository.countHealth(
                safeFilter.status(),
                safeFilter.reminderType(),
                safeFilter.scheduledFromInclusive(),
                safeFilter.scheduledThroughExclusive()
        );
    }

    public ReminderDeliveryHealthMetadata getMetadata() {
        securityService.requireAdmin();
        return new ReminderDeliveryHealthMetadata(
                timing.zoneId(),
                properties.getReminders().isEnabled(),
                properties.getReminders().getMaxAttempts()
        );
    }

    private ReminderDeliveryHealthFilter requireFilter(ReminderDeliveryHealthFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("Reminder delivery filter is required.");
        }
        return filter;
    }

    private ReminderDeliveryHealthItem toItem(ReminderDeliveryHealthProjection delivery, Instant now) {
        EnumSet<ReminderDeliveryHealthIndicator> indicators = EnumSet.noneOf(
                ReminderDeliveryHealthIndicator.class
        );
        LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        boolean outstanding = delivery.getDeliveryStatus() == InterviewReminderDeliveryStatus.PENDING
                || delivery.getDeliveryStatus() == InterviewReminderDeliveryStatus.FAILED;
        boolean currentGeneration = delivery.getReminderGeneration() == delivery.getCurrentReminderGeneration();

        if (delivery.getDeliveryStatus() == InterviewReminderDeliveryStatus.PENDING
                && delivery.getClaimedAt() != null
                && !delivery.getClaimedAt()
                .plus(properties.getReminders().getStaleClaimTimeout())
                .isAfter(nowUtc)) {
            indicators.add(ReminderDeliveryHealthIndicator.STALE_PENDING_CLAIM);
        }
        if (outstanding && delivery.getAttemptCount() >= properties.getReminders().getMaxAttempts()) {
            indicators.add(ReminderDeliveryHealthIndicator.ATTEMPTS_EXHAUSTED);
        }
        if (!currentGeneration) {
            indicators.add(ReminderDeliveryHealthIndicator.OBSOLETE_GENERATION);
        }
        if ((outstanding && reminderWindowExpired(delivery, now))
                || "REMINDER_WINDOW_EXPIRED".equals(delivery.getStatusReason())) {
            indicators.add(ReminderDeliveryHealthIndicator.EXPIRED_REMINDER_WINDOW);
        }
        if (retryScheduled(delivery, currentGeneration, now)) {
            indicators.add(ReminderDeliveryHealthIndicator.RETRY_SCHEDULED);
        }

        return new ReminderDeliveryHealthItem(
                delivery.getDeliveryId(),
                delivery.getBookingReference(),
                delivery.getReminderType(),
                delivery.getScheduledStartAt(),
                delivery.getDeliveryStatus(),
                delivery.getAttemptCount(),
                delivery.getClaimedAt(),
                delivery.getNextAttemptAt(),
                delivery.getSentAt(),
                safeReason(delivery),
                indicators
        );
    }

    private boolean retryScheduled(
            ReminderDeliveryHealthProjection delivery,
            boolean currentGeneration,
            Instant now
    ) {
        if (delivery.getDeliveryStatus() != InterviewReminderDeliveryStatus.FAILED
                || delivery.getNextAttemptAt() == null
                || !properties.getReminders().isEnabled()
                || delivery.getAttemptCount() >= properties.getReminders().getMaxAttempts()
                || !currentGeneration
                || !currentlyEligible(delivery)) {
            return false;
        }
        LocalDateTime currentStart = currentScheduledStart(delivery);
        Instant nextAttempt = delivery.getNextAttemptAt().toInstant(ZoneOffset.UTC);
        Instant evaluationTime = nextAttempt.isAfter(now) ? nextAttempt : now;
        return timing.includesAt(delivery.getReminderType(), currentStart, evaluationTime);
    }

    private boolean currentlyEligible(ReminderDeliveryHealthProjection delivery) {
        return ELIGIBLE_BOOKING_STATUSES.contains(delivery.getCurrentBookingStatus())
                && Boolean.TRUE.equals(delivery.getApplicantActive())
                && delivery.getCurrentApplicantStatus() == ApplicantStatus.SCHEDULED
                && Boolean.TRUE.equals(delivery.getScheduleActive())
                && delivery.getCurrentScheduleStatus() != null
                && delivery.getCurrentScheduleStatus() != ScheduleStatus.CANCELLED
                && delivery.getCurrentScheduleDate() != null
                && delivery.getCurrentScheduleStartTime() != null;
    }

    private boolean reminderWindowExpired(ReminderDeliveryHealthProjection delivery, Instant now) {
        LocalDateTime start = delivery.getScheduledStartAt();
        if (start == null || delivery.getReminderType() == null) {
            return false;
        }
        LocalDateTime businessNow = LocalDateTime.ofInstant(now, timing.zoneId());
        LocalDateTime lowerBoundary = delivery.getReminderType() == InterviewReminderType.REMINDER_24H
                ? businessNow.plusHours(2)
                : businessNow;
        return !start.isAfter(lowerBoundary);
    }

    private LocalDateTime currentScheduledStart(ReminderDeliveryHealthProjection delivery) {
        if (delivery.getCurrentScheduleDate() == null || delivery.getCurrentScheduleStartTime() == null) {
            return null;
        }
        return LocalDateTime.of(delivery.getCurrentScheduleDate(), delivery.getCurrentScheduleStartTime());
    }

    private String safeReason(ReminderDeliveryHealthProjection delivery) {
        if (delivery.getDeliveryStatus() == InterviewReminderDeliveryStatus.SENT) {
            return SENT_REASON;
        }
        if (delivery.getStatusReason() == null) {
            return UNAVAILABLE_REASON;
        }
        return SAFE_REASONS.getOrDefault(delivery.getStatusReason(), UNAVAILABLE_REASON);
    }
}

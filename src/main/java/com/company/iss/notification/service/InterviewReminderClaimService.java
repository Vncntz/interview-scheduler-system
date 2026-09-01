package com.company.iss.notification.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.dto.InterviewReminderContext;
import com.company.iss.notification.entity.InterviewReminderDelivery;
import com.company.iss.notification.entity.InterviewReminderDeliveryStatus;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.notification.repository.InterviewReminderDeliveryRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

@Service
public class InterviewReminderClaimService {

    private static final EnumSet<BookingStatus> ELIGIBLE_STATUSES = EnumSet.of(
            BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED
    );

    private final BookingRepository bookingRepository;
    private final InterviewReminderDeliveryRepository deliveryRepository;
    private final InterviewReminderTiming timing;
    private final NotificationRuntimeProperties properties;
    private final SmtpConfigurationValidator emailValidator;

    public InterviewReminderClaimService(
            BookingRepository bookingRepository,
            InterviewReminderDeliveryRepository deliveryRepository,
            InterviewReminderTiming timing,
            NotificationRuntimeProperties properties,
            SmtpConfigurationValidator emailValidator
    ) {
        this.bookingRepository = bookingRepository;
        this.deliveryRepository = deliveryRepository;
        this.timing = timing;
        this.properties = properties;
        this.emailValidator = emailValidator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InterviewReminderClaimResult claimNew(Long bookingId, InterviewReminderType type) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null) {
            return InterviewReminderClaimResult.skipped();
        }
        if (deliveryRepository.findForUpdate(bookingId, booking.getReminderGeneration(), type).isPresent()) {
            return InterviewReminderClaimResult.duplicate();
        }

        LocalDateTime scheduledStart = scheduledStart(booking);
        if (scheduledStart == null) {
            return InterviewReminderClaimResult.skipped();
        }
        InterviewReminderDelivery delivery = InterviewReminderDelivery.pending(booking, type, scheduledStart);
        String reason = ineligibleReason(booking, scheduledStart, type);
        if (reason != null) {
            delivery.markSkipped(reason);
            deliveryRepository.saveAndFlush(delivery);
            return InterviewReminderClaimResult.skipped();
        }
        return claim(delivery, booking, scheduledStart);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InterviewReminderClaimResult claimRetry(
            Long bookingId,
            Long deliveryId,
            InterviewReminderType type
    ) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null) {
            return InterviewReminderClaimResult.skipped();
        }
        InterviewReminderDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null
                || !Objects.equals(delivery.getBooking().getId(), bookingId)
                || delivery.getReminderType() != type) {
            return InterviewReminderClaimResult.duplicate();
        }
        if (delivery.getReminderGeneration() != booking.getReminderGeneration()) {
            delivery.markSkipped("STALE_GENERATION");
            deliveryRepository.saveAndFlush(delivery);
            return InterviewReminderClaimResult.skipped();
        }
        if (!isClaimable(delivery)) {
            return InterviewReminderClaimResult.duplicate();
        }

        LocalDateTime scheduledStart = scheduledStart(booking);
        String reason = ineligibleReason(booking, scheduledStart, type);
        if (reason != null) {
            delivery.markSkipped(reason);
            deliveryRepository.saveAndFlush(delivery);
            return InterviewReminderClaimResult.skipped();
        }
        return claim(delivery, booking, scheduledStart);
    }

    private InterviewReminderClaimResult claim(
            InterviewReminderDelivery delivery,
            Booking booking,
            LocalDateTime scheduledStart
    ) {
        String token = UUID.randomUUID().toString();
        delivery.claim(token, timing.nowUtc());
        InterviewReminderDelivery saved = deliveryRepository.saveAndFlush(delivery);
        return InterviewReminderClaimResult.claimed(context(saved, booking, scheduledStart, token));
    }

    private boolean isClaimable(InterviewReminderDelivery delivery) {
        if (delivery.getAttemptCount() >= properties.getReminders().getMaxAttempts()) {
            if (delivery.getStatus() == InterviewReminderDeliveryStatus.FAILED
                    && delivery.getNextAttemptAt() != null) {
                delivery.markFailed(null, "MAX_ATTEMPTS_REACHED");
                deliveryRepository.saveAndFlush(delivery);
            }
            return false;
        }
        LocalDateTime now = timing.nowUtc();
        if (delivery.getStatus() == InterviewReminderDeliveryStatus.FAILED) {
            return delivery.getNextAttemptAt() != null && !delivery.getNextAttemptAt().isAfter(now);
        }
        if (delivery.getStatus() == InterviewReminderDeliveryStatus.PENDING) {
            return delivery.getClaimedAt() != null
                    && !delivery.getClaimedAt()
                    .plus(properties.getReminders().getStaleClaimTimeout())
                    .isAfter(now);
        }
        return false;
    }

    private String ineligibleReason(Booking booking, LocalDateTime start, InterviewReminderType type) {
        Applicant applicant = booking.getApplicant();
        Schedule schedule = booking.getSchedule();
        if (!ELIGIBLE_STATUSES.contains(booking.getStatus())) {
            return "BOOKING_NOT_ELIGIBLE";
        }
        if (applicant == null || !applicant.isActive() || applicant.getStatus() != ApplicantStatus.SCHEDULED) {
            return "APPLICANT_NOT_ELIGIBLE";
        }
        if (schedule == null || !schedule.isActive() || schedule.getStatus() == ScheduleStatus.CANCELLED) {
            return "SCHEDULE_NOT_ELIGIBLE";
        }
        if (!timing.includes(type, start)) {
            return "OUTSIDE_REMINDER_WINDOW";
        }
        try {
            emailValidator.validateRecipient(applicant.getEmail());
        } catch (SmtpConfigurationException exception) {
            return "INVALID_RECIPIENT";
        }
        return null;
    }

    private LocalDateTime scheduledStart(Booking booking) {
        Schedule schedule = booking.getSchedule();
        if (schedule == null || schedule.getScheduleDate() == null || schedule.getStartTime() == null) {
            return null;
        }
        return LocalDateTime.of(schedule.getScheduleDate(), schedule.getStartTime());
    }

    private InterviewReminderContext context(
            InterviewReminderDelivery delivery,
            Booking booking,
            LocalDateTime scheduledStart,
            String token
    ) {
        Applicant applicant = booking.getApplicant();
        Schedule schedule = booking.getSchedule();
        PositionOpening position = applicant.getPositionOpening();
        return new InterviewReminderContext(
                delivery.getId(), booking.getId(), booking.getReminderGeneration(), delivery.getReminderType(), token,
                applicant.getEmail(), applicant.getFullName(), booking.getBookingReference(),
                scheduledStart.atZone(timing.zoneId()),
                booking.getInterviewStage() == null ? "" : booking.getInterviewStage().name(),
                schedule.getInterviewMode() == null ? "" : schedule.getInterviewMode().name(),
                schedule.getRecruiter() == null ? "" : schedule.getRecruiter().getFullName(),
                schedule.getBranch() == null ? "" : schedule.getBranch().getBranchName(),
                position == null ? "" : position.getTitle(),
                position == null || position.getClient() == null ? "" : position.getClient().getCompanyName(),
                position == null ? "" : position.getWorkLocation()
        );
    }
}

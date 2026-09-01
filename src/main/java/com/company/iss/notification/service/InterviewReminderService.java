package com.company.iss.notification.service;

import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.dto.InterviewReminderRetryCandidate;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.notification.repository.InterviewReminderDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InterviewReminderService {

    private static final Logger log = LoggerFactory.getLogger(InterviewReminderService.class);
    private static final List<BookingStatus> ELIGIBLE_STATUSES = List.of(
            BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED
    );

    private final BookingRepository bookingRepository;
    private final InterviewReminderDeliveryRepository deliveryRepository;
    private final InterviewReminderClaimService claimService;
    private final InterviewReminderCompletionService completionService;
    private final NotificationService notificationService;
    private final InterviewReminderTiming timing;
    private final NotificationRuntimeProperties properties;

    public InterviewReminderService(
            BookingRepository bookingRepository,
            InterviewReminderDeliveryRepository deliveryRepository,
            InterviewReminderClaimService claimService,
            InterviewReminderCompletionService completionService,
            NotificationService notificationService,
            InterviewReminderTiming timing,
            NotificationRuntimeProperties properties
    ) {
        this.bookingRepository = bookingRepository;
        this.deliveryRepository = deliveryRepository;
        this.claimService = claimService;
        this.completionService = completionService;
        this.notificationService = notificationService;
        this.timing = timing;
        this.properties = properties;
    }

    public ProcessingSummary processDueReminders() {
        if (!properties.getReminders().isEnabled()) {
            return ProcessingSummary.empty();
        }

        MutableSummary summary = new MutableSummary();
        for (InterviewReminderType type : InterviewReminderType.values()) {
            processType(type, summary);
        }
        ProcessingSummary result = summary.snapshot();
        log.info(
                "[REMINDER] Scan complete candidates={} sent={} skipped={} failed={} duplicates={}",
                result.candidates(), result.sent(), result.skipped(), result.failed(), result.duplicates()
        );
        return result;
    }

    private void processType(InterviewReminderType type, MutableSummary summary) {
        int batchSize = properties.getReminders().getBatchSize();
        InterviewReminderTiming.Window window = timing.currentWindow(type);
        List<InterviewReminderRetryCandidate> retries = deliveryRepository.findRetryCandidates(
                type,
                ELIGIBLE_STATUSES,
                properties.getReminders().getMaxAttempts(),
                timing.nowUtc(),
                timing.nowUtc().minus(properties.getReminders().getStaleClaimTimeout()),
                window.lowerExclusive().toLocalDate(),
                window.lowerExclusive().toLocalTime(),
                window.upperInclusive().toLocalDate(),
                window.upperInclusive().toLocalTime(),
                PageRequest.of(0, batchSize)
        );
        for (InterviewReminderRetryCandidate retry : retries) {
            summary.candidates++;
            processClaim(
                    () -> claimService.claimRetry(retry.bookingId(), retry.deliveryId(), retry.reminderType()),
                    summary
            );
        }

        int remaining = batchSize - retries.size();
        if (remaining < 1) {
            return;
        }
        List<Long> bookingIds = bookingRepository.findReminderCandidateIds(
                ELIGIBLE_STATUSES,
                type,
                window.lowerExclusive().toLocalDate(),
                window.lowerExclusive().toLocalTime(),
                window.upperInclusive().toLocalDate(),
                window.upperInclusive().toLocalTime(),
                PageRequest.of(0, remaining)
        );
        for (Long bookingId : bookingIds) {
            summary.candidates++;
            processClaim(() -> claimService.claimNew(bookingId, type), summary);
        }
    }

    private void processClaim(ClaimOperation operation, MutableSummary summary) {
        InterviewReminderClaimResult claim;
        try {
            claim = operation.claim();
        } catch (DataIntegrityViolationException exception) {
            summary.duplicates++;
            return;
        } catch (RuntimeException exception) {
            summary.failed++;
            log.error("[REMINDER] Claim failed reason=REMINDER_CLAIM_FAILED");
            return;
        }

        if (claim.disposition() == InterviewReminderClaimResult.Disposition.SKIPPED) {
            summary.skipped++;
            return;
        }
        if (claim.disposition() == InterviewReminderClaimResult.Disposition.DUPLICATE) {
            summary.duplicates++;
            return;
        }

        ReminderNotificationResult notificationResult;
        try {
            notificationResult = notificationService.sendInterviewReminder(claim.context());
        } catch (RuntimeException exception) {
            notificationResult = ReminderNotificationResult.retryable("EMAIL_DELIVERY_FAILED");
        }
        try {
            if (!completionService.complete(claim.context(), notificationResult)) {
                summary.duplicates++;
                return;
            }
        } catch (RuntimeException exception) {
            summary.failed++;
            log.error(
                    "[REMINDER] Completion failed bookingId={} deliveryId={} type={} reason=REMINDER_COMPLETION_FAILED",
                    claim.context().bookingId(), claim.context().deliveryId(), claim.context().reminderType()
            );
            return;
        }

        switch (notificationResult.disposition()) {
            case SENT -> summary.sent++;
            case SKIPPED -> summary.skipped++;
            case RETRYABLE_FAILURE -> summary.failed++;
        }
    }

    @FunctionalInterface
    private interface ClaimOperation {
        InterviewReminderClaimResult claim();
    }

    private static final class MutableSummary {
        private int candidates;
        private int sent;
        private int skipped;
        private int failed;
        private int duplicates;

        private ProcessingSummary snapshot() {
            return new ProcessingSummary(candidates, sent, skipped, failed, duplicates);
        }
    }

    public record ProcessingSummary(int candidates, int sent, int skipped, int failed, int duplicates) {
        public static ProcessingSummary empty() {
            return new ProcessingSummary(0, 0, 0, 0, 0);
        }
    }
}

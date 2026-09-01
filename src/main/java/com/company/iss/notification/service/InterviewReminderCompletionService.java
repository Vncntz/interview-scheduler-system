package com.company.iss.notification.service;

import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.dto.InterviewReminderContext;
import com.company.iss.notification.entity.InterviewReminderDelivery;
import com.company.iss.notification.repository.InterviewReminderDeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class InterviewReminderCompletionService {

    private static final Set<String> ALLOWED_REASONS = Set.of(
            "EMAIL_DISABLED",
            "TEMPLATE_MISSING",
            "TEMPLATE_DISABLED",
            "INVALID_RECIPIENT",
            "TEMPLATE_RENDER_FAILED",
            "SMTP_CONFIGURATION_INVALID",
            "SMTP_AUTHENTICATION_FAILED",
            "SMTP_CONNECTION_FAILED",
            "SMTP_SEND_FAILED",
            "EMAIL_DELIVERY_FAILED",
            "INVALID_MESSAGE"
    );

    private final InterviewReminderDeliveryRepository deliveryRepository;
    private final InterviewReminderTiming timing;
    private final NotificationRuntimeProperties properties;

    public InterviewReminderCompletionService(
            InterviewReminderDeliveryRepository deliveryRepository,
            InterviewReminderTiming timing,
            NotificationRuntimeProperties properties
    ) {
        this.deliveryRepository = deliveryRepository;
        this.timing = timing;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(InterviewReminderContext context, ReminderNotificationResult result) {
        InterviewReminderDelivery delivery = deliveryRepository.findByIdForUpdate(context.deliveryId()).orElse(null);
        if (delivery == null || !delivery.hasClaim(context.claimToken())) {
            return false;
        }
        if (delivery.getReminderGeneration() != delivery.getBooking().getReminderGeneration()) {
            delivery.markSkipped("STALE_GENERATION");
            deliveryRepository.saveAndFlush(delivery);
            return true;
        }

        switch (result.disposition()) {
            case SENT -> delivery.markSent(timing.nowUtc());
            case SKIPPED -> delivery.markSkipped(allowedReason(result.reason()));
            case RETRYABLE_FAILURE -> markFailed(delivery, allowedReason(result.reason()));
        }
        deliveryRepository.saveAndFlush(delivery);
        return true;
    }

    private void markFailed(InterviewReminderDelivery delivery, String reason) {
        boolean exhausted = delivery.getAttemptCount() >= properties.getReminders().getMaxAttempts();
        boolean windowExpired = !timing.includesAfter(
                delivery.getReminderType(),
                delivery.getScheduledStartAt(),
                properties.getReminders().getRetryDelay()
        );
        LocalDateTime nextAttempt = exhausted || windowExpired
                ? null
                : timing.nowUtc().plus(properties.getReminders().getRetryDelay());
        String terminalReason = exhausted
                ? "MAX_ATTEMPTS_REACHED"
                : windowExpired ? "REMINDER_WINDOW_EXPIRED" : reason;
        delivery.markFailed(nextAttempt, terminalReason);
    }

    private String allowedReason(String reason) {
        return ALLOWED_REASONS.contains(reason) ? reason : "EMAIL_DELIVERY_FAILED";
    }
}

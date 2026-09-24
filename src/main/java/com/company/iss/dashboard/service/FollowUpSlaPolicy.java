package com.company.iss.dashboard.service;

import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.dashboard.config.FollowUpSlaProperties;
import com.company.iss.dashboard.dto.FollowUpSlaStatus;
import com.company.iss.shared.time.BusinessTime;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

@Component
public class FollowUpSlaPolicy {

    private final BusinessTime businessTime;
    private final FollowUpSlaProperties properties;

    public FollowUpSlaPolicy(BusinessTime businessTime, FollowUpSlaProperties properties) {
        this.businessTime = businessTime;
        this.properties = properties;
    }

    public LocalDateTime now() {
        return snapshot().dateTime();
    }

    public BusinessTime.Snapshot snapshot() {
        return businessTime.snapshot();
    }

    public Duration targetFor(InterviewStage stage) {
        return switch (requireFollowUpStage(stage)) {
            case FINAL -> properties.getFinalTarget();
            case CLIENT -> properties.getClientTarget();
            default -> throw new IllegalStateException("Unexpected follow-up stage.");
        };
    }

    public Duration dueSoonWindow() {
        return properties.getDueSoonWindow();
    }

    public FollowUpSlaStatus status(InterviewStage stage, LocalDateTime waitingSince, LocalDateTime now) {
        Objects.requireNonNull(waitingSince, "Follow-up waiting start is required.");
        LocalDateTime deadline = deadline(stage, waitingSince);
        if (!now.isBefore(deadline)) {
            return FollowUpSlaStatus.OVERDUE;
        }
        if (!now.isBefore(deadline.minus(dueSoonWindow()))) {
            return FollowUpSlaStatus.DUE_SOON;
        }
        return FollowUpSlaStatus.ON_TRACK;
    }

    public LocalDateTime deadline(InterviewStage stage, LocalDateTime waitingSince) {
        Objects.requireNonNull(waitingSince, "Follow-up waiting start is required.");
        return businessTime.plusElapsed(waitingSince, targetFor(stage));
    }

    public Duration elapsed(LocalDateTime waitingSince, LocalDateTime now) {
        if (waitingSince == null) {
            return null;
        }
        Objects.requireNonNull(now, "Follow-up calculation time is required.");
        return elapsed(waitingSince, businessTime.toInstantStrict(now));
    }

    public Duration elapsed(LocalDateTime waitingSince, Instant now) {
        if (waitingSince == null) {
            return null;
        }
        return businessTime.elapsed(waitingSince, now);
    }

    public InterviewStage requireFollowUpStage(InterviewStage stage) {
        if (stage != InterviewStage.FINAL && stage != InterviewStage.CLIENT) {
            throw new IllegalArgumentException("Follow-up stage must be FINAL or CLIENT.");
        }
        return stage;
    }
}

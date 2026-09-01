package com.company.iss.notification.service;

import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.entity.InterviewReminderType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class InterviewReminderTiming {

    private final Clock clock;
    private final NotificationRuntimeProperties properties;

    public InterviewReminderTiming(Clock clock, NotificationRuntimeProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public Window currentWindow(InterviewReminderType type) {
        return windowAt(type, clock.instant());
    }

    private Window windowAt(InterviewReminderType type, Instant instant) {
        LocalDateTime now = LocalDateTime.ofInstant(instant, zoneId());
        return switch (type) {
            case REMINDER_24H -> new Window(now.plusHours(2), now.plusHours(24));
            case REMINDER_2H -> new Window(now, now.plusHours(2));
        };
    }

    public boolean includes(InterviewReminderType type, LocalDateTime scheduledStart) {
        return includes(windowAt(type, clock.instant()), scheduledStart);
    }

    public boolean includesAfter(
            InterviewReminderType type,
            LocalDateTime scheduledStart,
            Duration delay
    ) {
        return includes(windowAt(type, clock.instant().plus(delay)), scheduledStart);
    }

    private boolean includes(Window window, LocalDateTime scheduledStart) {
        if (scheduledStart == null) {
            return false;
        }
        return scheduledStart.isAfter(window.lowerExclusive())
                && !scheduledStart.isAfter(window.upperInclusive());
    }

    public LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
    }

    public ZoneId zoneId() {
        return properties.getReminders().zoneId();
    }

    public record Window(LocalDateTime lowerExclusive, LocalDateTime upperInclusive) {
    }
}

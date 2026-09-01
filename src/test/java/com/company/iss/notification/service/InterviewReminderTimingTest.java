package com.company.iss.notification.service;

import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.entity.InterviewReminderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewReminderTimingTest {

    private InterviewReminderTiming timing;

    @BeforeEach
    void setUp() {
        NotificationRuntimeProperties properties = new NotificationRuntimeProperties();
        timing = new InterviewReminderTiming(
                Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC), properties
        );
    }

    @Test
    void twentyFourHourWindowIsLowerExclusiveAndUpperInclusiveInBusinessTime() {
        assertFalse(timing.includes(InterviewReminderType.REMINDER_24H, LocalDateTime.of(2026, 9, 1, 10, 0)));
        assertTrue(timing.includes(InterviewReminderType.REMINDER_24H, LocalDateTime.of(2026, 9, 1, 10, 0, 1)));
        assertTrue(timing.includes(InterviewReminderType.REMINDER_24H, LocalDateTime.of(2026, 9, 2, 8, 0)));
        assertFalse(timing.includes(InterviewReminderType.REMINDER_24H, LocalDateTime.of(2026, 9, 2, 8, 0, 1)));
    }

    @Test
    void twoHourWindowExcludesNowAndIncludesExactlyTwoHours() {
        assertFalse(timing.includes(InterviewReminderType.REMINDER_2H, LocalDateTime.of(2026, 9, 1, 8, 0)));
        assertTrue(timing.includes(InterviewReminderType.REMINDER_2H, LocalDateTime.of(2026, 9, 1, 8, 0, 1)));
        assertTrue(timing.includes(InterviewReminderType.REMINDER_2H, LocalDateTime.of(2026, 9, 1, 10, 0)));
        assertFalse(timing.includes(InterviewReminderType.REMINDER_2H, LocalDateTime.of(2026, 9, 1, 10, 0, 1)));
    }

    @Test
    void pastInterviewIsOutsideBothWindows() {
        LocalDateTime past = LocalDateTime.of(2026, 9, 1, 7, 59);
        assertFalse(timing.includes(InterviewReminderType.REMINDER_24H, past));
        assertFalse(timing.includes(InterviewReminderType.REMINDER_2H, past));
    }

    @Test
    void retryDelayMustRemainInsideTheReminderBand() {
        LocalDateTime startsSoon = LocalDateTime.of(2026, 9, 1, 8, 5);

        assertTrue(timing.includes(InterviewReminderType.REMINDER_2H, startsSoon));
        assertFalse(timing.includesAfter(
                InterviewReminderType.REMINDER_2H, startsSoon, Duration.ofMinutes(10)
        ));
    }
}

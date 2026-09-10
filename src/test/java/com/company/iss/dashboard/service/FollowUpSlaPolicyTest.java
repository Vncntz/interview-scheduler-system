package com.company.iss.dashboard.service;

import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.dashboard.config.FollowUpSlaProperties;
import com.company.iss.dashboard.dto.FollowUpSlaStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FollowUpSlaPolicyTest {

    private final FollowUpSlaPolicy policy = new FollowUpSlaPolicy(
            Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC),
            new FollowUpSlaProperties()
    );
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 7, 12, 0);

    @Test
    void finalStageUsesExactApproachingAndOverdueBoundaries() {
        assertEquals(FollowUpSlaStatus.ON_TRACK,
                policy.status(InterviewStage.FINAL, now.minusHours(47).minusMinutes(59), now));
        assertEquals(FollowUpSlaStatus.DUE_SOON,
                policy.status(InterviewStage.FINAL, now.minusHours(48), now));
        assertEquals(FollowUpSlaStatus.DUE_SOON,
                policy.status(InterviewStage.FINAL, now.minusHours(71).minusMinutes(59), now));
        assertEquals(FollowUpSlaStatus.OVERDUE,
                policy.status(InterviewStage.FINAL, now.minusHours(72), now));
    }

    @Test
    void clientTargetCountsWeekendHoursAsElapsedWallClockTime() {
        LocalDateTime fridayNoon = LocalDateTime.of(2026, 9, 4, 12, 0);
        assertEquals(FollowUpSlaStatus.DUE_SOON,
                policy.status(InterviewStage.CLIENT, fridayNoon, fridayNoon.plusHours(108)));
    }

    @Test
    void nowUsesConfiguredTimestampZone() {
        FollowUpSlaProperties properties = new FollowUpSlaProperties();
        properties.setTimestampZone(ZoneId.of("America/New_York"));
        FollowUpSlaPolicy zonedPolicy = new FollowUpSlaPolicy(
                Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC), properties
        );

        assertEquals(LocalDateTime.of(2026, 9, 7, 8, 0), zonedPolicy.now());
    }

    @Test
    void rejectsInitialAndNullStages() {
        assertThrows(IllegalArgumentException.class, () -> policy.targetFor(InterviewStage.INITIAL));
        assertThrows(IllegalArgumentException.class, () -> policy.targetFor(null));
    }
}

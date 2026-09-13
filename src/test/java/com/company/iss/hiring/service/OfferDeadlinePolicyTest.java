package com.company.iss.hiring.service;

import com.company.iss.hiring.config.OfferDeadlineProperties;
import com.company.iss.hiring.entity.OfferDeadlineState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferDeadlinePolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-13T02:00:00Z");

    @Test
    void classificationUsesExactOverdueAndDueSoonBoundariesWithoutMutation() {
        OfferDeadlinePolicy policy = policy(Duration.ofHours(24), ZoneOffset.UTC);
        LocalDateTime now = policy.now();

        assertEquals(OfferDeadlineState.NO_DEADLINE, policy.classify(null, now));
        assertEquals(OfferDeadlineState.OVERDUE, policy.classify(now, now));
        assertEquals(OfferDeadlineState.OVERDUE, policy.classify(now.minusNanos(1), now));
        assertEquals(OfferDeadlineState.DUE_SOON, policy.classify(now.plusNanos(1), now));
        assertEquals(OfferDeadlineState.DUE_SOON, policy.classify(now.plusHours(24), now));
        assertEquals(OfferDeadlineState.ON_TRACK, policy.classify(now.plusHours(24).plusNanos(1), now));
    }

    @Test
    void configurableWindowZoneFutureValidationAndAgeAreDeterministic() {
        OfferDeadlinePolicy policy = policy(Duration.ofHours(6), ZoneId.of("Asia/Manila"));
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);

        assertEquals(now, policy.now());
        assertEquals(OfferDeadlineState.DUE_SOON, policy.classify(now.plusHours(6), now));
        assertEquals(OfferDeadlineState.ON_TRACK, policy.classify(now.plusHours(6).plusMinutes(1), now));
        assertTrue(policy.isFuture(null, now));
        assertTrue(policy.isFuture(now.plusNanos(1), now));
        assertFalse(policy.isFuture(now, now));
        assertEquals(Duration.ofHours(30), policy.age(now.minusHours(30), now));
        assertEquals(Duration.ZERO, policy.age(now.plusMinutes(1), now));
    }

    private OfferDeadlinePolicy policy(Duration dueSoonWindow, ZoneId zone) {
        OfferDeadlineProperties properties = new OfferDeadlineProperties();
        properties.setDueSoonWindow(dueSoonWindow);
        properties.setTimestampZone(zone);
        return new OfferDeadlinePolicy(Clock.fixed(NOW, ZoneOffset.UTC), properties);
    }
}

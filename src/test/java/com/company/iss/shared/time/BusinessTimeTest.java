package com.company.iss.shared.time;

import com.company.iss.config.BusinessTimeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusinessTimeTest {

    private static final Instant MANILA_AFTER_MIDNIGHT = Instant.parse("2026-09-01T16:30:00.123456789Z");

    @Test
    void snapshotUsesOneInstantAndNormalizesPersistedBusinessTimeToMicroseconds() {
        AtomicInteger reads = new AtomicInteger();
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                reads.incrementAndGet();
                return MANILA_AFTER_MIDNIGHT;
            }
        };
        BusinessTimeProperties properties = new BusinessTimeProperties();
        BusinessTime businessTime = new BusinessTime(clock, properties);

        BusinessTime.Snapshot snapshot = businessTime.snapshot();

        assertEquals(1, reads.get());
        assertEquals(MANILA_AFTER_MIDNIGHT, snapshot.instant());
        assertEquals(LocalDate.of(2026, 9, 2), snapshot.date());
        assertEquals(LocalDateTime.of(2026, 9, 2, 0, 30, 0, 123_456_000), snapshot.dateTime());
        assertEquals(snapshot.dateTime().toLocalTime(), snapshot.time());
    }

    @Test
    @ResourceLock(Resources.TIME_ZONE)
    void explicitBusinessZoneIsIndependentOfJvmDefault() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            BusinessTime.Snapshot utcJvm = BusinessTimeTestFactory.at(MANILA_AFTER_MIDNIGHT).snapshot();
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"));
            BusinessTime.Snapshot manilaJvm = BusinessTimeTestFactory.at(MANILA_AFTER_MIDNIGHT).snapshot();

            assertEquals(utcJvm, manilaJvm);
            assertEquals(LocalDate.of(2026, 9, 2), manilaJvm.date());
            assertEquals(LocalDate.of(2026, 9, 1), LocalDateTime.ofInstant(
                    MANILA_AFTER_MIDNIGHT, ZoneOffset.UTC).toLocalDate());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void strictConversionRejectsDstGapAndOverlapInsteadOfChoosingAnOffset() {
        BusinessTime newYorkTime = BusinessTimeTestFactory.at(
                Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("America/New_York"));

        assertThrows(IllegalArgumentException.class,
                () -> newYorkTime.toInstantStrict(LocalDateTime.of(2026, 3, 8, 2, 30)));
        assertThrows(IllegalArgumentException.class,
                () -> newYorkTime.toInstantStrict(LocalDateTime.of(2026, 11, 1, 1, 30)));
    }
}

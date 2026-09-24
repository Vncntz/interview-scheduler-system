package com.company.iss.shared.time;

import com.company.iss.config.BusinessTimeProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Component
public class BusinessTime {

    private final Clock clock;
    private final BusinessTimeProperties properties;

    public BusinessTime(Clock clock, BusinessTimeProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public Snapshot snapshot() {
        Instant instant = clock.instant();
        ZoneId zone = properties.getZone();
        LocalDateTime dateTime = normalize(LocalDateTime.ofInstant(instant, zone));
        return new Snapshot(instant, zone, dateTime, dateTime.toLocalDate(), dateTime.toLocalTime());
    }

    public ZoneId zone() {
        return properties.getZone();
    }

    public LocalDateTime normalize(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

    public Instant toInstantStrict(LocalDateTime value) {
        Objects.requireNonNull(value, "Business timestamp is required.");
        ZoneId zone = zone();
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(value);
        if (offsets.size() != 1) {
            throw new IllegalArgumentException(
                    "Business timestamp %s is ambiguous or nonexistent in configured zone %s."
                            .formatted(value, zone)
            );
        }
        return value.toInstant(offsets.getFirst());
    }

    public Duration elapsed(LocalDateTime start, Instant end) {
        Objects.requireNonNull(end, "Business calculation instant is required.");
        Instant startInstant = toInstantStrict(start);
        return end.isBefore(startInstant) ? Duration.ZERO : Duration.between(startInstant, end);
    }

    public LocalDateTime plusElapsed(LocalDateTime start, Duration duration) {
        Objects.requireNonNull(duration, "Business duration is required.");
        Instant result = toInstantStrict(start).plus(duration);
        return normalize(LocalDateTime.ofInstant(result, zone()));
    }

    public record Snapshot(
            Instant instant,
            ZoneId zone,
            LocalDateTime dateTime,
            LocalDate date,
            LocalTime time
    ) {
    }
}

package com.company.iss.hiring.service;

import com.company.iss.hiring.config.OfferDeadlineProperties;
import com.company.iss.hiring.entity.OfferDeadlineState;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Component
public class OfferDeadlinePolicy {

    private final Clock clock;
    private final OfferDeadlineProperties properties;

    public OfferDeadlinePolicy(Clock clock, OfferDeadlineProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), properties.getTimestampZone());
    }

    public LocalDateTime normalize(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

    public LocalDateTime dueSoonCutoff(LocalDateTime now) {
        return Objects.requireNonNull(now, "Offer deadline calculation time is required.")
                .plus(properties.getDueSoonWindow());
    }

    public boolean isFuture(LocalDateTime deadline, LocalDateTime now) {
        return deadline == null || deadline.isAfter(Objects.requireNonNull(
                now, "Offer deadline calculation time is required."));
    }

    public OfferDeadlineState classify(LocalDateTime deadline, LocalDateTime now) {
        Objects.requireNonNull(now, "Offer deadline calculation time is required.");
        if (deadline == null) {
            return OfferDeadlineState.NO_DEADLINE;
        }
        if (!deadline.isAfter(now)) {
            return OfferDeadlineState.OVERDUE;
        }
        if (!deadline.isAfter(dueSoonCutoff(now))) {
            return OfferDeadlineState.DUE_SOON;
        }
        return OfferDeadlineState.ON_TRACK;
    }

    public Duration age(LocalDateTime offeredAt, LocalDateTime now) {
        if (offeredAt == null) {
            return null;
        }
        Objects.requireNonNull(now, "Offer age calculation time is required.");
        Instant offeredInstant = offeredAt.atZone(properties.getTimestampZone()).toInstant();
        Instant nowInstant = now.atZone(properties.getTimestampZone()).toInstant();
        return nowInstant.isBefore(offeredInstant)
                ? Duration.ZERO
                : Duration.between(offeredInstant, nowInstant);
    }
}

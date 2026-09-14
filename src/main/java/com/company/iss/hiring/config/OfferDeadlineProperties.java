package com.company.iss.hiring.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRules;

@Component
@ConfigurationProperties(prefix = "iss.hiring.offer-deadline")
@Validated
@Getter
@Setter
public class OfferDeadlineProperties {

    // Hiring timestamps were introduced after this boundary and are stored without offsets.
    static final Instant HIRING_RECORDS_START = Instant.parse("2026-01-01T00:00:00Z");

    @NotNull
    private Duration dueSoonWindow = Duration.ofHours(24);

    /** Zone used to interpret the application's zone-less hiring timestamps. */
    @NotNull
    private ZoneId timestampZone = ZoneId.of("Asia/Manila");

    @AssertTrue(message = "The offer deadline due-soon window must be positive.")
    public boolean isValidTiming() {
        return dueSoonWindow != null && !dueSoonWindow.isZero() && !dueSoonWindow.isNegative();
    }

    @AssertTrue(message = "Offer timestamp zone must not have fall-back overlaps in the supported hiring-record era because hiring timestamps are stored without an offset.")
    public boolean isTimestampZoneWithoutFallBackOverlaps() {
        if (timestampZone == null) {
            return true;
        }

        return isFreeOfFallBackOverlapsInSupportedHiringRecordEra(timestampZone.getRules());
    }

    static boolean isFreeOfFallBackOverlapsInSupportedHiringRecordEra(ZoneRules rules) {
        boolean hasRelevantExplicitOverlap = rules.getTransitions().stream()
                .anyMatch(transition -> transition.isOverlap()
                        && !transition.getInstant().isBefore(HIRING_RECORDS_START));
        boolean hasRecurringOverlap = rules.getTransitionRules().stream()
                .anyMatch(rule -> rule.getOffsetAfter().getTotalSeconds()
                        < rule.getOffsetBefore().getTotalSeconds());
        return !hasRelevantExplicitOverlap && !hasRecurringOverlap;
    }
}

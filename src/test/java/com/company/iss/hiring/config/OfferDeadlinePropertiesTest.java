package com.company.iss.hiring.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferDeadlinePropertiesTest {

    @Test
    void suppliesDocumentedPositiveDefaults() {
        OfferDeadlineProperties properties = new OfferDeadlineProperties();

        assertEquals(Duration.ofHours(24), properties.getDueSoonWindow());
        assertEquals(ZoneId.of("Asia/Manila"), properties.getTimestampZone());
        assertTrue(properties.isValidTiming());
        assertTrue(properties.isTimestampZoneWithoutFallBackOverlaps());
    }

    @Test
    void bindsWindowAndTimestampZoneOverrides() {
        OfferDeadlineProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
                "iss.hiring.offer-deadline.due-soon-window", "12h",
                "iss.hiring.offer-deadline.timestamp-zone", "Asia/Manila"
        ))).bind("iss.hiring.offer-deadline", Bindable.of(OfferDeadlineProperties.class))
                .orElseThrow(() -> new AssertionError("Properties were not bound."));

        assertEquals(Duration.ofHours(12), properties.getDueSoonWindow());
        assertEquals(ZoneId.of("Asia/Manila"), properties.getTimestampZone());
    }

    @Test
    void validationAcceptsManilaAndUtcTimestampZones() {
        OfferDeadlineProperties properties = new OfferDeadlineProperties();
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();

            properties.setTimestampZone(ZoneId.of("Asia/Manila"));
            assertTrue(validator.validate(properties).isEmpty());

            properties.setTimestampZone(ZoneId.of("UTC"));
            assertTrue(validator.validate(properties).isEmpty());
        }
    }

    @Test
    void validationRejectsTimestampZonesWithFallBackOverlaps() {
        OfferDeadlineProperties properties = new OfferDeadlineProperties();
        properties.setTimestampZone(ZoneId.of("America/New_York"));

        assertFalse(properties.isTimestampZoneWithoutFallBackOverlaps());
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(properties);

            assertEquals(1, violations.size());
            assertEquals(
                    "Offer timestamp zone must not have fall-back overlaps in the supported hiring-record era because hiring timestamps are stored without an offset.",
                    violations.iterator().next().getMessage()
            );
        }
    }

    @Test
    void explicitFallBackOverlapBoundaryMatchesSupportedHiringRecordEra() {
        ZoneRules historicalOverlap = rulesWithExplicitFallBackAt(LocalDateTime.of(2025, 1, 1, 1, 0));
        ZoneRules overlapAtBoundary = rulesWithExplicitFallBackAt(LocalDateTime.of(2026, 1, 1, 1, 0));

        assertTrue(OfferDeadlineProperties.isFreeOfFallBackOverlapsInSupportedHiringRecordEra(historicalOverlap));
        assertFalse(OfferDeadlineProperties.isFreeOfFallBackOverlapsInSupportedHiringRecordEra(overlapAtBoundary));
    }

    @Test
    void nullTimestampZoneIsRejectedOnlyByNotNullValidation() {
        OfferDeadlineProperties properties = new OfferDeadlineProperties();
        properties.setTimestampZone(null);

        assertTrue(properties.isTimestampZoneWithoutFallBackOverlaps());
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(properties);

            assertEquals(1, violations.size());
            assertEquals("must not be null", violations.iterator().next().getMessage());
        }
    }

    @Test
    void validationRejectsNullZeroAndNegativeDueSoonWindows() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            OfferDeadlineProperties properties = new OfferDeadlineProperties();

            properties.setDueSoonWindow(null);
            assertFalse(properties.isValidTiming());
            assertFalse(validator.validate(properties).isEmpty());

            properties.setDueSoonWindow(Duration.ZERO);
            assertFalse(properties.isValidTiming());
            assertFalse(validator.validate(properties).isEmpty());

            properties.setDueSoonWindow(Duration.ofSeconds(-1));
            assertFalse(properties.isValidTiming());
            assertFalse(validator.validate(properties).isEmpty());
        }
    }

    private ZoneRules rulesWithExplicitFallBackAt(LocalDateTime transitionTime) {
        ZoneOffset offsetBefore = ZoneOffset.ofHours(1);
        ZoneOffset offsetAfter = ZoneOffset.UTC;
        ZoneOffsetTransition transition = ZoneOffsetTransition.of(transitionTime, offsetBefore, offsetAfter);
        return ZoneRules.of(offsetAfter, offsetBefore, List.of(), List.of(transition), List.of());
    }
}

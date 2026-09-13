package com.company.iss.hiring.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferDeadlinePropertiesTest {

    @Test
    void suppliesDocumentedPositiveDefaults() {
        OfferDeadlineProperties properties = new OfferDeadlineProperties();

        assertEquals(Duration.ofHours(24), properties.getDueSoonWindow());
        assertTrue(properties.isValidTiming());
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
    void validationRejectsNonPositiveWindow() {
        OfferDeadlineProperties properties = new OfferDeadlineProperties();
        properties.setDueSoonWindow(Duration.ZERO);

        assertFalse(properties.isValidTiming());
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(validatorFactory.getValidator().validate(properties).isEmpty());
        }
    }
}

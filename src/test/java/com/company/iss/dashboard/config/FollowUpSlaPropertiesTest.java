package com.company.iss.dashboard.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowUpSlaPropertiesTest {

    @Test
    void suppliesDocumentedDefaults() {
        FollowUpSlaProperties properties = new FollowUpSlaProperties();

        assertEquals(Duration.ofHours(72), properties.getFinalTarget());
        assertEquals(Duration.ofHours(120), properties.getClientTarget());
        assertEquals(Duration.ofHours(24), properties.getDueSoonWindow());
        assertTrue(properties.isValidTiming());
    }

    @Test
    void bindsSpringDurationOverrides() {
        FollowUpSlaProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
                "iss.dashboard.follow-up.final-target", "96h",
                "iss.dashboard.follow-up.client-target", "144h",
                "iss.dashboard.follow-up.due-soon-window", "12h",
                "iss.dashboard.follow-up.timestamp-zone", "Asia/Manila"
        ))).bind("iss.dashboard.follow-up", Bindable.of(FollowUpSlaProperties.class))
                .orElseThrow(() -> new AssertionError("Properties were not bound."));

        assertEquals(Duration.ofHours(96), properties.getFinalTarget());
        assertEquals(Duration.ofHours(144), properties.getClientTarget());
        assertEquals(Duration.ofHours(12), properties.getDueSoonWindow());
        assertEquals(java.time.ZoneId.of("Asia/Manila"), properties.getTimestampZone());
        assertTrue(properties.isValidTiming());
    }

    @Test
    void validationRejectsNonPositiveOrTooLongDueSoonWindows() {
        FollowUpSlaProperties properties = new FollowUpSlaProperties();
        properties.setFinalTarget(Duration.ZERO);
        assertFalse(properties.isValidTiming());

        properties.setFinalTarget(Duration.ofHours(72));
        properties.setDueSoonWindow(Duration.ofHours(72));
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(validatorFactory.getValidator().validate(properties).isEmpty());
        }
    }
}

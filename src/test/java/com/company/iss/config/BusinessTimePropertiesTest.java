package com.company.iss.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessTimePropertiesTest {

    @Test
    void acceptsTransitionFreeCanonicalZonesAndEquivalentAliases() {
        BusinessTimeProperties properties = new BusinessTimeProperties();
        assertTrue(properties.isZoneWithoutFutureTransitions());

        properties.setZone(ZoneId.of("+08:00"));
        assertTrue(properties.isZoneWithoutFutureTransitions());
    }

    @Test
    void rejectsZoneWithRecurringDstGapsAndOverlaps() {
        BusinessTimeProperties properties = new BusinessTimeProperties();
        properties.setZone(ZoneId.of("America/New_York"));

        assertFalse(properties.isZoneWithoutFutureTransitions());
    }
}

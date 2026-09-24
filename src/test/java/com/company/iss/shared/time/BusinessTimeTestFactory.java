package com.company.iss.shared.time;

import com.company.iss.config.BusinessTimeProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class BusinessTimeTestFactory {

    private BusinessTimeTestFactory() {
    }

    public static BusinessTime at(Instant instant) {
        return at(instant, ZoneId.of("Asia/Manila"));
    }

    public static BusinessTime at(Instant instant, ZoneId businessZone) {
        BusinessTimeProperties properties = new BusinessTimeProperties();
        properties.setZone(businessZone);
        return new BusinessTime(Clock.fixed(instant, ZoneOffset.UTC), properties);
    }

    public static BusinessTime.Snapshot snapshotAt(Instant instant) {
        return at(instant).snapshot();
    }
}

package com.company.iss.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRules;

@Component
@ConfigurationProperties(prefix = "iss.business-time")
@Validated
@Getter
@Setter
public class BusinessTimeProperties {

    public static final Instant BUSINESS_RECORDS_START = Instant.parse("2026-01-01T00:00:00Z");

    @NotNull
    private ZoneId zone = ZoneId.of("Asia/Manila");

    @AssertTrue(message = "Business time zone must not have daylight-saving gaps or overlaps in the supported business-record era because appointment and event timestamps are stored without an offset.")
    public boolean isZoneWithoutFutureTransitions() {
        return zone == null || isFreeOfTransitionsInSupportedBusinessRecordEra(zone.getRules());
    }

    static boolean isFreeOfTransitionsInSupportedBusinessRecordEra(ZoneRules rules) {
        boolean hasRelevantExplicitTransition = rules.getTransitions().stream()
                .anyMatch(transition -> !transition.getInstant().isBefore(BUSINESS_RECORDS_START));
        return !hasRelevantExplicitTransition && rules.getTransitionRules().isEmpty();
    }
}

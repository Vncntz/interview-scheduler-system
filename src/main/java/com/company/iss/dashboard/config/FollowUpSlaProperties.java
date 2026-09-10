package com.company.iss.dashboard.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "iss.dashboard.follow-up")
@Validated
@Getter
@Setter
public class FollowUpSlaProperties {

    @NotNull
    private Duration finalTarget = Duration.ofHours(72);

    @NotNull
    private Duration clientTarget = Duration.ofHours(120);

    @NotNull
    private Duration dueSoonWindow = Duration.ofHours(24);

    /** Zone used to interpret the application's zone-less operational timestamps. */
    @NotNull
    private ZoneId timestampZone = ZoneId.systemDefault();

    @AssertTrue(message = "Follow-up targets must be positive and the due-soon window must be shorter than both targets.")
    public boolean isValidTiming() {
        return isPositive(finalTarget)
                && isPositive(clientTarget)
                && isPositive(dueSoonWindow)
                && dueSoonWindow.compareTo(finalTarget) < 0
                && dueSoonWindow.compareTo(clientTarget) < 0;
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}

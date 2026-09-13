package com.company.iss.hiring.config;

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
@ConfigurationProperties(prefix = "iss.hiring.offer-deadline")
@Validated
@Getter
@Setter
public class OfferDeadlineProperties {

    @NotNull
    private Duration dueSoonWindow = Duration.ofHours(24);

    /** Zone used to interpret the application's zone-less hiring timestamps. */
    @NotNull
    private ZoneId timestampZone = ZoneId.systemDefault();

    @AssertTrue(message = "The offer deadline due-soon window must be positive.")
    public boolean isValidTiming() {
        return dueSoonWindow != null && !dueSoonWindow.isZero() && !dueSoonWindow.isNegative();
    }
}

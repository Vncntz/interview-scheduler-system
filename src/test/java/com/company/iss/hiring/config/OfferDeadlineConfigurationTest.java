package com.company.iss.hiring.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferDeadlineConfigurationTest {

    private static final String OVERLAP_MESSAGE =
            "Offer timestamp zone must not have fall-back overlaps in the supported hiring-record era because hiring timestamps are stored without an offset.";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void supportedTimestampZonesStartAndBind() {
        contextRunner.withPropertyValues("iss.hiring.offer-deadline.timestamp-zone=Asia/Manila")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(
                            ZoneId.of("Asia/Manila"),
                            context.getBean(OfferDeadlineProperties.class).getTimestampZone()
                    );
                });

        contextRunner.withPropertyValues("iss.hiring.offer-deadline.timestamp-zone=UTC")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(
                            ZoneId.of("UTC"),
                            context.getBean(OfferDeadlineProperties.class).getTimestampZone()
                    );
                });
    }

    @Test
    void fallBackOverlapZoneFailsStartupValidationClearly() {
        contextRunner.withPropertyValues("iss.hiring.offer-deadline.timestamp-zone=America/New_York")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    assertTrue(hasMessageInCauseChain(failure, OVERLAP_MESSAGE));
                });
    }

    private boolean hasMessageInCauseChain(Throwable failure, String expectedMessage) {
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expectedMessage)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OfferDeadlineProperties.class)
    static class TestConfiguration {
    }
}

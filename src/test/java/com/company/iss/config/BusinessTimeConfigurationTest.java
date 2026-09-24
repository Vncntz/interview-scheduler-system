package com.company.iss.config;

import com.company.iss.dashboard.config.FollowUpSlaProperties;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.core.env.ConfigurableEnvironment;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessTimeConfigurationTest {

    private static final String TRANSITION_MESSAGE =
            "Business time zone must not have daylight-saving gaps or overlaps in the supported business-record era because appointment and event timestamps are stored without an offset.";

    @Test
    void transitionFreeCanonicalZoneBinds() {
        contextRunner(0)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(
                            ZoneId.of("Asia/Manila"),
                            context.getBean(BusinessTimeProperties.class).getZone()
                    );
                });
    }

    @Test
    void recurringDstZoneFailsStartupValidationClearly() {
        contextRunner(0).withPropertyValues("iss.business-time.zone=America/New_York")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    assertTrue(hasMessageInCauseChain(failure, TRANSITION_MESSAGE));
                });
    }

    @Test
    void directNonDefaultCanonicalPropertyDrivesLegacyFallbacksOnEmptyInstallation() {
        contextRunner(0).withPropertyValues("iss.business-time.zone=UTC")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(ZoneId.of("UTC"), context.getBean(BusinessTimeProperties.class).getZone());
                    assertEquals(ZoneId.of("UTC"), context.getBean(FollowUpSlaProperties.class).getTimestampZone());
                    assertEquals(
                            ZoneId.of("UTC"),
                            context.getBean(NotificationRuntimeProperties.class).getReminders().zoneId()
                    );
                });
    }

    @Test
    void directCanonicalPropertyIsExplicitForExistingSchedules() {
        contextRunner(1).withPropertyValues("iss.business-time.zone=UTC")
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void packagedDefaultIsNotExplicitForExistingSchedules() {
        contextRunner(1).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure, () -> canonicalPropertySources(context.getEnvironment()));
            assertTrue(hasMessageInCauseChain(
                    failure,
                    "Set BUSINESS_TIME_ZONE or iss.business-time.zone explicitly"
            ));
        });
    }

    private String canonicalPropertySources(ConfigurableEnvironment environment) {
        StringBuilder sources = new StringBuilder();
        environment.getPropertySources().forEach(source -> {
            if (source.getProperty("iss.business-time.zone") != null) {
                sources.append(source.getName())
                        .append(" origin=")
                        .append(OriginLookup.getOrigin(source, "iss.business-time.zone"))
                        .append(System.lineSeparator());
            }
        });
        return sources.toString();
    }

    private ApplicationContextRunner contextRunner(long scheduleCount) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(TestConfiguration.class)
                .withBean(ScheduleRepository.class, () -> scheduleRepository(scheduleCount));
    }

    private ScheduleRepository scheduleRepository(long scheduleCount) {
        ScheduleRepository repository = mock(ScheduleRepository.class);
        when(repository.count()).thenReturn(scheduleCount);
        return repository;
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
    @EnableConfigurationProperties({
            BusinessTimeProperties.class,
            FollowUpSlaProperties.class,
            NotificationRuntimeProperties.class
    })
    @Import(BusinessTimeZoneStartupGuard.class)
    static class TestConfiguration {
    }
}

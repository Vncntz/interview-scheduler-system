package com.company.iss.config;

import com.company.iss.dashboard.config.FollowUpSlaProperties;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessTimeZoneStartupGuardTest {

    private ScheduleRepository scheduleRepository;
    private MockEnvironment environment;
    private BusinessTimeProperties businessProperties;
    private FollowUpSlaProperties followUpProperties;
    private NotificationRuntimeProperties notificationProperties;
    private BusinessTimeZoneStartupGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        scheduleRepository = mock(ScheduleRepository.class);
        environment = new MockEnvironment();
        businessProperties = new BusinessTimeProperties();
        followUpProperties = new FollowUpSlaProperties();
        followUpProperties.setTimestampZone(ZoneId.of("Asia/Manila"));
        notificationProperties = new NotificationRuntimeProperties();

        ObjectProvider<BusinessTimeProperties> businessProvider = mock(ObjectProvider.class);
        ObjectProvider<FollowUpSlaProperties> followUpProvider = mock(ObjectProvider.class);
        ObjectProvider<NotificationRuntimeProperties> notificationProvider = mock(ObjectProvider.class);
        when(businessProvider.getObject()).thenReturn(businessProperties);
        when(followUpProvider.getObject()).thenReturn(followUpProperties);
        when(notificationProvider.getObject()).thenReturn(notificationProperties);
        guard = new BusinessTimeZoneStartupGuard(
                scheduleRepository, environment, businessProvider, followUpProvider, notificationProvider
        );
    }

    @Test
    void emptyInstallationMayUseSafeDefault() {
        when(scheduleRepository.count()).thenReturn(0L);

        assertDoesNotThrow(guard::afterSingletonsInstantiated);
    }

    @Test
    void existingSchedulesRequireExplicitHistoricalCanonicalZone() {
        when(scheduleRepository.count()).thenReturn(1L);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, guard::afterSingletonsInstantiated);

        assertEquals(
                "Existing schedules use zone-less appointment and business timestamps. Set BUSINESS_TIME_ZONE or "
                        + "iss.business-time.zone explicitly to their historical zone before startup; the "
                        + "Asia/Manila default is safe only when no schedules exist. Existing values are not "
                        + "reinterpreted or backfilled.",
                failure.getMessage()
        );
    }

    @Test
    void directCanonicalPropertyAllowsExistingSchedules() {
        environment.setProperty("iss.business-time.zone", "Asia/Manila");
        when(scheduleRepository.count()).thenReturn(1L);

        assertDoesNotThrow(guard::afterSingletonsInstantiated);
        verify(scheduleRepository, never()).count();
    }

    @Test
    void higherPrecedenceDirectCanonicalPropertyWinsOverEnvironmentFallback() {
        environment.setProperty("BUSINESS_TIME_ZONE", "Asia/Manila");
        environment.setProperty("iss.business-time.zone", "UTC");
        businessProperties.setZone(ZoneId.of("UTC"));
        followUpProperties.setTimestampZone(ZoneId.of("UTC"));
        notificationProperties.getReminders().setBusinessZone("UTC");

        assertDoesNotThrow(guard::afterSingletonsInstantiated);
        verify(scheduleRepository, never()).count();
    }

    @Test
    void equivalentLegacyAliasesAllowExistingSchedules() {
        environment.setProperty("iss.business-time.zone", "Asia/Manila");
        businessProperties.setZone(ZoneId.of("+08:00"));
        followUpProperties.setTimestampZone(ZoneId.of("Asia/Manila"));
        notificationProperties.getReminders().setBusinessZone("+08:00");

        assertDoesNotThrow(guard::afterSingletonsInstantiated);
    }

    @Test
    void conflictingLegacyEffectiveZoneFailsBeforeHistoricalDataCheck() {
        followUpProperties.setTimestampZone(ZoneId.of("UTC"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, guard::afterSingletonsInstantiated);

        assertEquals(
                "The effective follow-up time zone (UTC) must be equivalent to canonical business time zone "
                        + "(Asia/Manila) for zone-less historical timestamps.",
                failure.getMessage()
        );
        verify(scheduleRepository, never()).count();
    }

    @Test
    void blankCanonicalEnvironmentVariableFailsClearly() {
        environment.setProperty("BUSINESS_TIME_ZONE", "   ");

        IllegalStateException failure = assertThrows(IllegalStateException.class, guard::afterPropertiesSet);

        assertEquals("BUSINESS_TIME_ZONE must not be blank.", failure.getMessage());
    }

    @Test
    void blankDirectCanonicalPropertyFailsClearly() {
        environment.setProperty("iss.business-time.zone", "   ");

        IllegalStateException failure = assertThrows(IllegalStateException.class, guard::afterPropertiesSet);

        assertEquals("iss.business-time.zone must not be blank.", failure.getMessage());
    }
}

package com.company.iss.notification.service;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.dto.ReminderDeliveryHealthFilter;
import com.company.iss.notification.dto.ReminderDeliveryHealthIndicator;
import com.company.iss.notification.entity.InterviewReminderDeliveryStatus;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.notification.repository.InterviewReminderDeliveryRepository;
import com.company.iss.notification.repository.ReminderDeliveryHealthProjection;
import com.company.iss.schedule.entity.ScheduleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderDeliveryHealthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    private InterviewReminderDeliveryRepository repository;
    private SecurityService securityService;
    private NotificationRuntimeProperties properties;
    private ReminderDeliveryHealthService service;

    @BeforeEach
    void setUp() {
        repository = mock(InterviewReminderDeliveryRepository.class);
        securityService = mock(SecurityService.class);
        properties = new NotificationRuntimeProperties();
        properties.getReminders().setEnabled(true);
        properties.getReminders().setBusinessZone("Asia/Manila");
        properties.getReminders().setMaxAttempts(3);
        properties.getReminders().setStaleClaimTimeout(Duration.ofMinutes(10));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ReminderDeliveryHealthService(
                repository,
                securityService,
                new InterviewReminderTiming(
                        clock,
                        com.company.iss.shared.time.BusinessTimeTestFactory.at(NOW)
                ),
                properties,
                clock
        );
    }

    @Test
    void everyPublicReadRequiresAdministratorAccess() {
        doThrow(new AccessDeniedException("denied")).when(securityService).requireAdmin();

        assertThrows(AccessDeniedException.class,
                () -> service.findPage(ReminderDeliveryHealthFilter.empty(), 0, 50));
        assertThrows(AccessDeniedException.class,
                () -> service.count(ReminderDeliveryHealthFilter.empty()));
        assertThrows(AccessDeniedException.class, service::getMetadata);
        verify(repository, never()).findHealthPage(any(), any(), any(), any(), any());
        verify(repository, never()).countHealth(any(), any(), any(), any());
    }

    @Test
    void pageUsesExactOffsetBoundedLimitAndInclusiveDateFilter() {
        ReminderDeliveryHealthFilter filter = new ReminderDeliveryHealthFilter(
                InterviewReminderDeliveryStatus.FAILED,
                InterviewReminderType.REMINDER_2H,
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 4)
        );
        when(repository.findHealthPage(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.findPage(filter, 25, 10);

        verify(repository).findHealthPage(
                eq(InterviewReminderDeliveryStatus.FAILED),
                eq(InterviewReminderType.REMINDER_2H),
                eq(LocalDateTime.of(2026, 9, 2, 0, 0)),
                eq(LocalDateTime.of(2026, 9, 5, 0, 0)),
                argThat(page -> page.getOffset() == 25 && page.getPageSize() == 10)
        );
        assertThrows(IllegalArgumentException.class,
                () -> service.findPage(ReminderDeliveryHealthFilter.empty(), 0, 101));
        assertThrows(IllegalArgumentException.class,
                () -> new ReminderDeliveryHealthFilter(null, null,
                        LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 4)));
    }

    @Test
    void boundaryEvidenceCanShowStaleExhaustedExpiredAndObsoleteTogether() {
        ReminderDeliveryHealthProjection projection = projection(
                InterviewReminderDeliveryStatus.PENDING,
                InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 1, 10, 0)
        );
        when(projection.getClaimedAt()).thenReturn(LocalDateTime.of(2026, 8, 31, 23, 50));
        when(projection.getAttemptCount()).thenReturn(3);
        when(projection.getReminderGeneration()).thenReturn(0);
        when(projection.getCurrentReminderGeneration()).thenReturn(1);
        when(repository.findHealthPage(any(), any(), any(), any(), any())).thenReturn(List.of(projection));

        Set<ReminderDeliveryHealthIndicator> indicators = service.findPage(
                ReminderDeliveryHealthFilter.empty(), 0, 50
        ).getFirst().healthIndicators();

        assertEquals(Set.of(
                ReminderDeliveryHealthIndicator.STALE_PENDING_CLAIM,
                ReminderDeliveryHealthIndicator.ATTEMPTS_EXHAUSTED,
                ReminderDeliveryHealthIndicator.EXPIRED_REMINDER_WINDOW,
                ReminderDeliveryHealthIndicator.OBSOLETE_GENERATION
        ), indicators);
    }

    @Test
    void pendingClaimIsNotStaleImmediatelyBeforeConfiguredBoundary() {
        ReminderDeliveryHealthProjection projection = projection(
                InterviewReminderDeliveryStatus.PENDING,
                InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 8, 0)
        );
        when(projection.getClaimedAt()).thenReturn(LocalDateTime.of(2026, 8, 31, 23, 50, 0, 1));
        when(repository.findHealthPage(any(), any(), any(), any(), any())).thenReturn(List.of(projection));

        Set<ReminderDeliveryHealthIndicator> indicators = service.findPage(
                ReminderDeliveryHealthFilter.empty(), 0, 50
        ).getFirst().healthIndicators();

        assertFalse(indicators.contains(ReminderDeliveryHealthIndicator.STALE_PENDING_CLAIM));
    }

    @Test
    void twoHourWindowExpiresAtItsExclusiveLowerBoundary() {
        ReminderDeliveryHealthProjection atBoundary = projection(
                InterviewReminderDeliveryStatus.PENDING,
                InterviewReminderType.REMINDER_2H,
                LocalDateTime.of(2026, 9, 1, 8, 0)
        );
        ReminderDeliveryHealthProjection justInsideWindow = projection(
                InterviewReminderDeliveryStatus.PENDING,
                InterviewReminderType.REMINDER_2H,
                LocalDateTime.of(2026, 9, 1, 8, 0, 0, 1)
        );
        when(repository.findHealthPage(any(), any(), any(), any(), any()))
                .thenReturn(List.of(atBoundary, justInsideWindow));

        var items = service.findPage(ReminderDeliveryHealthFilter.empty(), 0, 50);

        assertTrue(items.get(0).healthIndicators().contains(
                ReminderDeliveryHealthIndicator.EXPIRED_REMINDER_WINDOW
        ));
        assertFalse(items.get(1).healthIndicators().contains(
                ReminderDeliveryHealthIndicator.EXPIRED_REMINDER_WINDOW
        ));
    }

    @Test
    void failedDeliveryShowsRetryOnlyWhenFutureSchedulerRulesStillQualifyIt() {
        ReminderDeliveryHealthProjection qualifying = projection(
                InterviewReminderDeliveryStatus.FAILED,
                InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 8, 5)
        );
        when(qualifying.getNextAttemptAt()).thenReturn(LocalDateTime.of(2026, 9, 1, 0, 5));
        when(qualifying.getAttemptCount()).thenReturn(1);
        when(qualifying.getCurrentScheduleDate()).thenReturn(LocalDate.of(2026, 9, 2));
        when(qualifying.getCurrentScheduleStartTime()).thenReturn(LocalTime.of(8, 5));
        ReminderDeliveryHealthProjection lowerBoundary = projection(
                InterviewReminderDeliveryStatus.FAILED,
                InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 1, 10, 5)
        );
        when(lowerBoundary.getNextAttemptAt()).thenReturn(LocalDateTime.of(2026, 9, 1, 0, 5));
        when(lowerBoundary.getAttemptCount()).thenReturn(1);
        when(lowerBoundary.getCurrentScheduleDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(lowerBoundary.getCurrentScheduleStartTime()).thenReturn(LocalTime.of(10, 5));
        when(repository.findHealthPage(any(), any(), any(), any(), any()))
                .thenReturn(List.of(qualifying, lowerBoundary));

        var items = service.findPage(ReminderDeliveryHealthFilter.empty(), 0, 50);

        assertTrue(items.get(0).healthIndicators().contains(ReminderDeliveryHealthIndicator.RETRY_SCHEDULED));
        assertFalse(items.get(1).healthIndicators().contains(ReminderDeliveryHealthIndicator.RETRY_SCHEDULED));
    }

    @Test
    void retryIndicatorRejectsEachSchedulerDisqualifierAndTerminalStatus() {
        ReminderDeliveryHealthProjection obsolete = retryCandidate(InterviewReminderDeliveryStatus.FAILED);
        when(obsolete.getCurrentReminderGeneration()).thenReturn(1);

        ReminderDeliveryHealthProjection inactiveApplicant = retryCandidate(
                InterviewReminderDeliveryStatus.FAILED
        );
        when(inactiveApplicant.getApplicantActive()).thenReturn(false);

        ReminderDeliveryHealthProjection inactiveSchedule = retryCandidate(
                InterviewReminderDeliveryStatus.FAILED
        );
        when(inactiveSchedule.getScheduleActive()).thenReturn(false);

        ReminderDeliveryHealthProjection ineligibleBooking = retryCandidate(
                InterviewReminderDeliveryStatus.FAILED
        );
        when(ineligibleBooking.getCurrentBookingStatus()).thenReturn(BookingStatus.ATTENDED);

        ReminderDeliveryHealthProjection exhausted = retryCandidate(InterviewReminderDeliveryStatus.FAILED);
        when(exhausted.getAttemptCount()).thenReturn(3);

        ReminderDeliveryHealthProjection sent = retryCandidate(InterviewReminderDeliveryStatus.SENT);

        when(repository.findHealthPage(any(), any(), any(), any(), any())).thenReturn(List.of(
                obsolete, inactiveApplicant, inactiveSchedule, ineligibleBooking, exhausted, sent
        ));

        var items = service.findPage(ReminderDeliveryHealthFilter.empty(), 0, 50);

        assertTrue(items.stream().noneMatch(item -> item.healthIndicators().contains(
                ReminderDeliveryHealthIndicator.RETRY_SCHEDULED
        )));
    }

    @Test
    void currentAttemptLimitReclassifiesOutstandingButNeverTerminalDeliveries() {
        ReminderDeliveryHealthProjection failed = projection(
                InterviewReminderDeliveryStatus.FAILED,
                InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 8, 0)
        );
        when(failed.getAttemptCount()).thenReturn(3);
        ReminderDeliveryHealthProjection sent = projection(
                InterviewReminderDeliveryStatus.SENT,
                InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 8, 0)
        );
        when(sent.getAttemptCount()).thenReturn(3);
        when(repository.findHealthPage(any(), any(), any(), any(), any()))
                .thenReturn(List.of(failed, sent));

        var atOriginalLimit = service.findPage(ReminderDeliveryHealthFilter.empty(), 0, 50);
        properties.getReminders().setMaxAttempts(4);
        var afterLimitIncrease = service.findPage(ReminderDeliveryHealthFilter.empty(), 0, 50);

        assertTrue(atOriginalLimit.get(0).healthIndicators().contains(
                ReminderDeliveryHealthIndicator.ATTEMPTS_EXHAUSTED
        ));
        assertFalse(atOriginalLimit.get(1).healthIndicators().contains(
                ReminderDeliveryHealthIndicator.ATTEMPTS_EXHAUSTED
        ));
        assertFalse(afterLimitIncrease.get(0).healthIndicators().contains(
                ReminderDeliveryHealthIndicator.ATTEMPTS_EXHAUSTED
        ));
    }

    @Test
    void missingTimestampsAndUnknownReasonsRemainExplicitlyUnavailableAndSafe() {
        ReminderDeliveryHealthProjection missing = projection(
                InterviewReminderDeliveryStatus.PENDING,
                InterviewReminderType.REMINDER_2H,
                LocalDateTime.of(2026, 9, 2, 8, 0)
        );
        when(missing.getStatusReason()).thenReturn(null);
        ReminderDeliveryHealthProjection unknown = projection(
                InterviewReminderDeliveryStatus.FAILED,
                InterviewReminderType.REMINDER_2H,
                LocalDateTime.of(2026, 9, 2, 8, 0)
        );
        when(unknown.getStatusReason()).thenReturn("provider-secret-response user@example.test");
        when(repository.findHealthPage(any(), any(), any(), any(), any()))
                .thenReturn(List.of(missing, unknown));

        var items = service.findPage(ReminderDeliveryHealthFilter.empty(), 0, 50);

        assertNull(items.getFirst().claimedAtUtc());
        assertFalse(items.getFirst().healthIndicators().contains(
                ReminderDeliveryHealthIndicator.STALE_PENDING_CLAIM
        ));
        assertEquals("Delivery status detail unavailable.", items.getFirst().safeStatusReason());
        assertEquals("Delivery status detail unavailable.", items.get(1).safeStatusReason());
        assertFalse(items.get(1).safeStatusReason().contains("example.test"));
    }

    @Test
    void sentStatusDescribesSmtpAcceptanceWithoutClaimingInboxDelivery() {
        ReminderDeliveryHealthProjection sent = projection(
                InterviewReminderDeliveryStatus.SENT,
                InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 8, 0)
        );
        when(repository.findHealthPage(any(), any(), any(), any(), any())).thenReturn(List.of(sent));

        String reason = service.findPage(ReminderDeliveryHealthFilter.empty(), 0, 50)
                .getFirst().safeStatusReason();

        assertTrue(reason.contains("SMTP accepted"));
        assertTrue(reason.contains("not confirmed"));
    }

    @Test
    void persistedExpiredReasonAndDisabledSchedulerAreReportedAccurately() {
        properties.getReminders().setEnabled(false);
        ReminderDeliveryHealthProjection failed = projection(
                InterviewReminderDeliveryStatus.FAILED,
                InterviewReminderType.REMINDER_2H,
                LocalDateTime.of(2026, 9, 2, 8, 0)
        );
        when(failed.getStatusReason()).thenReturn("REMINDER_WINDOW_EXPIRED");
        when(failed.getNextAttemptAt()).thenReturn(LocalDateTime.of(2026, 9, 1, 0, 5));
        when(failed.getAttemptCount()).thenReturn(1);
        when(repository.findHealthPage(any(), any(), any(), any(), any())).thenReturn(List.of(failed));

        var item = service.findPage(ReminderDeliveryHealthFilter.empty(), 0, 50).getFirst();

        assertTrue(item.healthIndicators().contains(ReminderDeliveryHealthIndicator.EXPIRED_REMINDER_WINDOW));
        assertFalse(item.healthIndicators().contains(ReminderDeliveryHealthIndicator.RETRY_SCHEDULED));
    }

    private ReminderDeliveryHealthProjection projection(
            InterviewReminderDeliveryStatus status,
            InterviewReminderType type,
            LocalDateTime scheduledStart
    ) {
        ReminderDeliveryHealthProjection projection = mock(ReminderDeliveryHealthProjection.class);
        when(projection.getDeliveryId()).thenReturn(1L);
        when(projection.getBookingReference()).thenReturn("BK-SAFE");
        when(projection.getDeliveryStatus()).thenReturn(status);
        when(projection.getReminderType()).thenReturn(type);
        when(projection.getScheduledStartAt()).thenReturn(scheduledStart);
        when(projection.getCurrentBookingStatus()).thenReturn(BookingStatus.BOOKED);
        when(projection.getApplicantActive()).thenReturn(true);
        when(projection.getCurrentApplicantStatus()).thenReturn(ApplicantStatus.SCHEDULED);
        when(projection.getScheduleActive()).thenReturn(true);
        when(projection.getCurrentScheduleStatus()).thenReturn(ScheduleStatus.OPEN);
        when(projection.getCurrentScheduleDate()).thenReturn(scheduledStart.toLocalDate());
        when(projection.getCurrentScheduleStartTime()).thenReturn(scheduledStart.toLocalTime());
        return projection;
    }

    private ReminderDeliveryHealthProjection retryCandidate(InterviewReminderDeliveryStatus status) {
        ReminderDeliveryHealthProjection projection = projection(
                status,
                InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 8, 5)
        );
        when(projection.getNextAttemptAt()).thenReturn(LocalDateTime.of(2026, 9, 1, 0, 5));
        when(projection.getAttemptCount()).thenReturn(1);
        return projection;
    }
}

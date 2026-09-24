package com.company.iss.dashboard.service;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.dashboard.config.FollowUpSlaProperties;
import com.company.iss.dashboard.dto.FollowUpDeadlineFilter;
import com.company.iss.dashboard.dto.FollowUpSlaStatus;
import com.company.iss.dashboard.repository.FollowUpApplicantProjection;
import com.company.iss.dashboard.repository.FollowUpWorkloadProjection;
import com.company.iss.dashboard.repository.RecruiterFollowUpRepository;
import com.company.iss.shared.pagination.OffsetLimitPageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruiterWorkbenchServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T04:00:00Z");

    @Mock BookingRepository bookingRepository;
    @Mock RecruiterFollowUpRepository followUpRepository;
    @Mock SecurityService securityService;

    private RecruiterWorkbenchService service;
    private FollowUpSlaPolicy policy;

    @BeforeEach
    void setUp() {
        FollowUpSlaProperties properties = new FollowUpSlaProperties();
        properties.setTimestampZone(ZoneId.of("Asia/Manila"));
        policy = new FollowUpSlaPolicy(
                com.company.iss.shared.time.BusinessTimeTestFactory.at(NOW, ZoneId.of("Asia/Manila")),
                properties
        );
        service = new RecruiterWorkbenchService(
                bookingRepository, followUpRepository, securityService, policy
        );
    }

    @Test
    void loadScopesEveryQueueAndBothSummariesToAuthenticatedRecruiterBranch() {
        Branch branch = branch(17L);
        User recruiter = recruiter(23L, branch);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        stubBookingQueues(recruiter, branch);
        FollowUpWorkloadProjection emptyWorkload = workload(0, 0, 0, 0, 0);
        when(followUpRepository.summarizeFollowUpsByStage(eq(17L), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyWorkload);

        var data = service.load();

        assertEquals(InterviewStage.FINAL, data.finalInterviewFollowUp().stage());
        assertEquals(Duration.ofHours(72), data.finalInterviewFollowUp().target());
        assertEquals(InterviewStage.CLIENT, data.clientInterviewFollowUp().stage());
        assertEquals(Duration.ofHours(120), data.clientInterviewFollowUp().target());
        verify(bookingRepository).findPendingConfirmationsByScheduleAndApplicantBranch(
                17L, BookingStatus.BOOKED
        );
        verify(followUpRepository).summarizeFollowUpsByStage(eq(17L), eq(InterviewStage.FINAL),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(followUpRepository).summarizeFollowUpsByStage(eq(17L), eq(InterviewStage.CLIENT),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void stagePageUsesExactWindowAndMapsSeparateTimingFields() {
        Branch branch = branch(17L);
        when(securityService.requireOperationsUser()).thenReturn(recruiter(23L, branch));
        LocalDateTime waitingSince = policy.now().minusHours(60);
        FollowUpApplicantProjection projection = projection(waitingSince);
        when(followUpRepository.findFollowUpsByStage(eq(17L), eq(InterviewStage.FINAL), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class)))
                .thenReturn(List.of(projection));

        var result = service.findFollowUpPage(
                InterviewStage.FINAL, FollowUpDeadlineFilter.DUE_SOON, 7, 25
        ).getFirst();

        assertEquals(waitingSince.plusHours(72), result.dueAt());
        assertEquals(Duration.ofHours(60), result.elapsed());
        assertEquals(FollowUpSlaStatus.DUE_SOON, result.deadlineStatus());
        assertEquals(LocalDateTime.of(2026, 9, 8, 9, 30), result.relatedAppointmentAt());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(followUpRepository).findFollowUpsByStage(eq(17L), eq(InterviewStage.FINAL), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(),
                eq(FollowUpDeadlineFilter.DUE_SOON.name()), any(), any(), pageable.capture());
        assertInstanceOf(OffsetLimitPageable.class, pageable.getValue());
        assertEquals(7, pageable.getValue().getOffset());
        assertEquals(25, pageable.getValue().getPageSize());
    }

    @Test
    void pageClampsFutureElapsedToZero() {
        when(securityService.requireOperationsUser()).thenReturn(recruiter(23L, branch(17L)));
        FollowUpApplicantProjection projection = projection(policy.now().plusHours(2));
        when(followUpRepository.findFollowUpsByStage(any(), eq(InterviewStage.FINAL), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class)))
                .thenReturn(List.of(projection));

        assertEquals(Duration.ZERO, service.findFollowUpPage(
                InterviewStage.FINAL, FollowUpDeadlineFilter.ALL, 0, 25
        ).getFirst().elapsed());
    }

    @Test
    void missingHistoricalTimingMapsToExplicitNullableDeadlineFields() {
        when(securityService.requireOperationsUser()).thenReturn(recruiter(23L, branch(17L)));
        FollowUpApplicantProjection projection = mock(FollowUpApplicantProjection.class);
        when(projection.getApplicantId()).thenReturn(31L);
        when(projection.getBranchId()).thenReturn(17L);
        when(projection.getApplicantStatus()).thenReturn(ApplicantStatus.FOR_FINAL_INTERVIEW);
        when(followUpRepository.findFollowUpsByStage(any(), eq(InterviewStage.FINAL), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class))).thenReturn(List.of(projection));

        var result = service.findFollowUpPage(
                InterviewStage.FINAL, FollowUpDeadlineFilter.TIMING_UNAVAILABLE, 0, 25
        ).getFirst();

        assertNull(result.relatedAppointmentAt());
        assertNull(result.waitingSince());
        assertNull(result.dueAt());
        assertNull(result.elapsed());
        assertNull(result.deadlineStatus());
    }

    @Test
    void summaryUsesInclusiveCutoffsAndReturnedAggregateCounts() {
        when(securityService.requireOperationsUser()).thenReturn(recruiter(23L, branch(17L)));
        FollowUpWorkloadProjection workload = workload(7, 3, 2, 1, 1);
        when(followUpRepository.summarizeFollowUpsByStage(eq(17L), eq(InterviewStage.CLIENT), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(workload);
        ArgumentCaptor<LocalDateTime> overdue = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> approaching = ArgumentCaptor.forClass(LocalDateTime.class);

        var summary = service.getFollowUpSummary(InterviewStage.CLIENT);

        assertEquals(7, summary.total());
        assertEquals(3, summary.onTrack());
        assertEquals(2, summary.dueSoon());
        assertEquals(1, summary.overdue());
        assertEquals(1, summary.timingUnavailable());
        verify(followUpRepository).summarizeFollowUpsByStage(eq(17L), eq(InterviewStage.CLIENT),
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
                overdue.capture(), approaching.capture());
        assertEquals(policy.now().minusHours(120), overdue.getValue());
        assertEquals(policy.now().minusHours(96), approaching.getValue());
    }

    @Test
    void rejectsInvalidStagesAndPageWindowsBeforeQuerying() {
        when(securityService.requireOperationsUser()).thenReturn(recruiter(23L, branch(17L)));

        assertThrows(IllegalArgumentException.class,
                () -> service.findFollowUpPage(InterviewStage.INITIAL, FollowUpDeadlineFilter.ALL, 0, 25));
        assertThrows(IllegalArgumentException.class,
                () -> service.findFollowUpPage(InterviewStage.FINAL, FollowUpDeadlineFilter.ALL, -1, 25));
        assertThrows(IllegalArgumentException.class,
                () -> service.findFollowUpPage(InterviewStage.FINAL, FollowUpDeadlineFilter.ALL, 0, 101));
        assertThrows(IllegalArgumentException.class,
                () -> service.findFollowUpPage(InterviewStage.FINAL, null, 0, 25));
        verify(followUpRepository, never()).findFollowUpsByStage(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any()
        );
    }

    @Test
    void administratorsAndRecruitersWithoutPersistedBranchAreDenied() {
        User admin = new User();
        admin.setRole(Role.ADMIN);
        when(securityService.requireOperationsUser()).thenReturn(admin);
        assertThrows(AccessDeniedException.class,
                () -> service.getFollowUpSummary(InterviewStage.FINAL));

        User recruiter = recruiter(23L, null);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        assertThrows(AccessDeniedException.class,
                () -> service.findFollowUpPage(
                        InterviewStage.FINAL, FollowUpDeadlineFilter.ALL, 0, 25
                ));
    }

    private void stubBookingQueues(User recruiter, Branch branch) {
        when(bookingRepository.findTodaysAssignedForRecruiterAndApplicantBranch(
                eq(recruiter.getId()), eq(branch.getId()), any(LocalDate.class), any()
        )).thenReturn(List.of());
        when(bookingRepository.findUpcomingAssignedForRecruiterAndApplicantBranch(
                eq(recruiter.getId()), eq(branch.getId()), any(), any(), any()
        )).thenReturn(List.of());
        when(bookingRepository.findPendingConfirmationsByScheduleAndApplicantBranch(
                branch.getId(), BookingStatus.BOOKED
        )).thenReturn(List.of());
        when(bookingRepository.findDueAttendanceByScheduleAndApplicantBranch(
                eq(branch.getId()), eq(BookingStatus.CONFIRMED), any(), any()
        ))
                .thenReturn(List.of());
        when(bookingRepository.findOverdueUnevaluatedByApplicantBranch(
                eq(branch.getId()), eq(BookingStatus.ATTENDED), any(), any()
        )).thenReturn(List.of());
    }

    private FollowUpApplicantProjection projection(LocalDateTime waitingSince) {
        FollowUpApplicantProjection projection = mock(FollowUpApplicantProjection.class);
        when(projection.getApplicantId()).thenReturn(31L);
        when(projection.getBranchId()).thenReturn(17L);
        when(projection.getApplicantName()).thenReturn("Applicant 31");
        when(projection.getPositionTitle()).thenReturn("Engineer");
        when(projection.getClientName()).thenReturn("Client");
        when(projection.getApplicantStatus()).thenReturn(ApplicantStatus.FOR_FINAL_INTERVIEW);
        when(projection.getRelatedAppointmentDate()).thenReturn(LocalDate.of(2026, 9, 8));
        when(projection.getRelatedAppointmentStartTime()).thenReturn(LocalTime.of(9, 30));
        when(projection.getWaitingSince()).thenReturn(waitingSince);
        return projection;
    }

    private FollowUpWorkloadProjection workload(
            long total, long onTrack, long dueSoon, long overdue, long timingUnavailable
    ) {
        FollowUpWorkloadProjection projection = mock(FollowUpWorkloadProjection.class);
        when(projection.getTotal()).thenReturn(total);
        when(projection.getOnTrack()).thenReturn(onTrack);
        when(projection.getDueSoon()).thenReturn(dueSoon);
        when(projection.getOverdue()).thenReturn(overdue);
        when(projection.getTimingUnavailable()).thenReturn(timingUnavailable);
        return projection;
    }

    private Branch branch(Long id) {
        Branch branch = new Branch();
        branch.setId(id);
        return branch;
    }

    private User recruiter(Long id, Branch branch) {
        User recruiter = new User();
        recruiter.setId(id);
        recruiter.setRole(Role.RECRUITER);
        recruiter.setActive(true);
        recruiter.setBranch(branch);
        return recruiter;
    }
}

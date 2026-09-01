package com.company.iss.dashboard.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.dashboard.repository.FollowUpApplicantProjection;
import com.company.iss.dashboard.repository.RecruiterFollowUpRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.schedule.entity.Schedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruiterWorkbenchServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock RecruiterFollowUpRepository followUpRepository;
    @Mock SecurityService securityService;

    private RecruiterWorkbenchService service;

    @BeforeEach
    void setUp() {
        service = new RecruiterWorkbenchService(bookingRepository, followUpRepository, securityService);
    }

    @Test
    void everyBranchQueueUsesTheAuthenticatedRecruitersBranch() {
        Branch branch = branch(17L);
        User recruiter = recruiter(23L, branch);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        stubBookingQueues(recruiter, branch);
        when(followUpRepository.findFollowUps(eq(17L), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.load();

        verify(bookingRepository).findByScheduleBranchIdAndStatusOrderByScheduleScheduleDateAscScheduleStartTimeAsc(
                17L, BookingStatus.BOOKED
        );
        verify(bookingRepository).findDueByBranchAndStatus(
                eq(17L), eq(BookingStatus.CONFIRMED), any(LocalDate.class), any(LocalTime.class)
        );
        verify(bookingRepository).findOverdueUnevaluatedByBranch(
                eq(17L), eq(BookingStatus.ATTENDED), any(LocalDate.class), any(LocalTime.class)
        );
        verify(followUpRepository).findFollowUps(
                eq(17L), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void workbenchDtoIncludesApplicantIdAndInterviewStage() {
        Branch branch = branch(17L);
        User recruiter = recruiter(23L, branch);
        recruiter.setFullName("Maria Santos");
        Applicant applicant = new Applicant();
        applicant.setId(31L);
        applicant.setFirstName("Alex");
        applicant.setLastName("Candidate");
        PositionOpening position = new PositionOpening();
        position.setTitle("Engineer");
        applicant.setPositionOpening(position);
        Schedule schedule = new Schedule();
        schedule.setScheduleDate(LocalDate.now());
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setRecruiter(recruiter);
        Booking booking = Booking.forInterviewStage(InterviewStage.FINAL);
        booking.setId(41L);
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setBookingReference("BK-41");
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(bookingRepository.findByScheduleRecruiterIdAndScheduleScheduleDateAndStatusInOrderByScheduleStartTime(
                eq(23L), any(LocalDate.class), any()
        )).thenReturn(List.of(booking));
        when(bookingRepository.findUpcomingAssigned(eq(23L), any(), any(), any())).thenReturn(List.of());
        when(bookingRepository.findByScheduleBranchIdAndStatusOrderByScheduleScheduleDateAscScheduleStartTimeAsc(
                17L, BookingStatus.BOOKED
        )).thenReturn(List.of());
        when(bookingRepository.findDueByBranchAndStatus(eq(17L), eq(BookingStatus.CONFIRMED), any(), any()))
                .thenReturn(List.of());
        when(bookingRepository.findOverdueUnevaluatedByBranch(eq(17L), eq(BookingStatus.ATTENDED), any(), any()))
                .thenReturn(List.of());
        when(followUpRepository.findFollowUps(eq(17L), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        var item = service.load().todaysAssigned().getFirst();

        assertEquals(31L, item.applicantId());
        assertEquals(InterviewStage.FINAL, item.interviewStage());
    }

    @Test
    void mapsAndSplitsFollowUpsThroughTheCentralStagePolicy() {
        Branch branch = branch(17L);
        User recruiter = recruiter(23L, branch);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        stubBookingQueues(recruiter, branch);
        LocalDateTime older = LocalDateTime.of(2026, 8, 25, 9, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 8, 28, 9, 0);
        FollowUpApplicantProjection finalProgression = projection(
                31L, ApplicantStatus.FOR_FINAL_INTERVIEW, null, null, older
        );
        FollowUpApplicantProjection clientReplacement = projection(
                32L, ApplicantStatus.SCHEDULED, BookingStatus.NO_SHOW, InterviewStage.CLIENT, newer
        );
        when(followUpRepository.findFollowUps(eq(17L), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(finalProgression, clientReplacement));

        var data = service.load();

        assertEquals(List.of(31L), data.finalInterviewFollowUps().stream().map(item -> item.applicantId()).toList());
        assertEquals(InterviewStage.FINAL, data.finalInterviewFollowUps().getFirst().requiredStage());
        assertEquals(List.of(32L), data.clientInterviewFollowUps().stream().map(item -> item.applicantId()).toList());
        assertEquals(InterviewStage.CLIENT, data.clientInterviewFollowUps().getFirst().requiredStage());
        assertEquals(newer, data.clientInterviewFollowUps().getFirst().waitingSince());
    }

    @Test
    void administratorsRemainDeniedFromRecruiterWorkbenchService() {
        User admin = new User();
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        when(securityService.requireOperationsUser()).thenReturn(admin);

        assertThrows(AccessDeniedException.class, service::load);
    }

    private void stubBookingQueues(User recruiter, Branch branch) {
        when(bookingRepository.findByScheduleRecruiterIdAndScheduleScheduleDateAndStatusInOrderByScheduleStartTime(
                eq(recruiter.getId()), any(LocalDate.class), any()
        )).thenReturn(List.of());
        when(bookingRepository.findUpcomingAssigned(eq(recruiter.getId()), any(), any(), any())).thenReturn(List.of());
        when(bookingRepository.findByScheduleBranchIdAndStatusOrderByScheduleScheduleDateAscScheduleStartTimeAsc(
                branch.getId(), BookingStatus.BOOKED
        )).thenReturn(List.of());
        when(bookingRepository.findDueByBranchAndStatus(eq(branch.getId()), eq(BookingStatus.CONFIRMED), any(), any()))
                .thenReturn(List.of());
        when(bookingRepository.findOverdueUnevaluatedByBranch(eq(branch.getId()), eq(BookingStatus.ATTENDED), any(), any()))
                .thenReturn(List.of());
    }

    private FollowUpApplicantProjection projection(
            Long applicantId,
            ApplicantStatus status,
            BookingStatus bookingStatus,
            InterviewStage bookingStage,
            LocalDateTime waitingSince
    ) {
        FollowUpApplicantProjection projection = mock(FollowUpApplicantProjection.class);
        when(projection.getApplicantId()).thenReturn(applicantId);
        when(projection.getBranchId()).thenReturn(17L);
        when(projection.getApplicantName()).thenReturn("Applicant " + applicantId);
        when(projection.getPositionTitle()).thenReturn("Engineer");
        when(projection.getClientName()).thenReturn("Client");
        when(projection.getApplicantStatus()).thenReturn(status);
        when(projection.getMostRecentBookingStatus()).thenReturn(bookingStatus);
        when(projection.getMostRecentBookingStage()).thenReturn(bookingStage);
        when(projection.getWaitingSince()).thenReturn(waitingSince);
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

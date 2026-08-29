package com.company.iss.booking.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.repository.BookingRescheduleHistoryRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.repository.ScheduleRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingWorkflowSecurityTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingRescheduleHistoryRepository historyRepository;
    @Mock InterviewEvaluationRepository evaluationRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock ApplicantService applicantService;
    @Mock SecurityService securityService;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private BookingService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(securityService.requireOperationsUser(any(String.class)))
                .thenAnswer(invocation -> securityService.getCurrentUser());
        service = new BookingService(
                bookingRepository,
                historyRepository,
                evaluationRepository,
                scheduleRepository,
                applicantService,
                securityService,
                eventPublisher
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forcedPasswordChangeCannotReachBookingMutationState() {
        User forced = recruiter(1L);
        forced.setEmail("forced@example.test");
        forced.setMustChangePassword(true);
        BookingService guarded = serviceWithActualSecurity(forced);

        assertThrows(AccessDeniedException.class, () -> guarded.cancel(50L));

        verify(bookingRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(scheduleRepository, historyRepository, applicantService, eventPublisher);
    }

    @Test
    void currentlyLockedUserCannotReachBookingMutationState() {
        User locked = recruiter(1L);
        locked.setEmail("locked@example.test");
        locked.setLockoutUntil(LocalDateTime.of(2026, 8, 28, 2, 15));
        BookingService guarded = serviceWithActualSecurity(locked);

        assertThrows(AccessDeniedException.class, () -> guarded.confirm(50L));

        verify(bookingRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(scheduleRepository, historyRepository, applicantService, eventPublisher);
    }

    @Test
    void recruiterCannotConfirmGuessedBookingFromAnotherBranch() {
        when(securityService.getCurrentUser()).thenReturn(recruiter(1L));
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(booking(50L, 2L, BookingStatus.BOOKED)));

        assertThrows(AccessDeniedException.class, () -> service.confirm(50L));

        verify(bookingRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void attendedTransitionUpdatesBookingAndApplicantAtomically() {
        Booking booking = booking(50L, 1L, BookingStatus.CONFIRMED);
        when(securityService.getCurrentUser()).thenReturn(recruiter(1L));
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(booking));

        service.markAttended(50L);

        assertEquals(BookingStatus.ATTENDED, booking.getStatus());
        assertEquals(ApplicantStatus.INTERVIEWED, booking.getApplicant().getStatus());
        verify(bookingRepository).save(booking);
    }

    @Test
    void noShowRejectsAnyStateOtherThanConfirmedWithoutWrite() {
        when(securityService.getCurrentUser()).thenReturn(recruiter(1L));
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(booking(50L, 1L, BookingStatus.BOOKED)));

        assertThrows(BusinessRuleViolationException.class, () -> service.markNoShow(50L));

        verify(bookingRepository, never()).save(any());
    }

    private User recruiter(Long branchId) {
        Branch branch = new Branch();
        branch.setId(branchId);
        User user = new User();
        user.setRole(Role.RECRUITER);
        user.setActive(true);
        user.setBranch(branch);
        return user;
    }

    private BookingService serviceWithActualSecurity(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user.getEmail(), "n/a", List.of())
        );
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        SecurityService actualSecurity = new SecurityService(
                userRepository,
                Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC)
        );
        return new BookingService(
                bookingRepository,
                historyRepository,
                evaluationRepository,
                scheduleRepository,
                applicantService,
                actualSecurity,
                eventPublisher
        );
    }

    private Booking booking(Long id, Long branchId, BookingStatus status) {
        Branch branch = new Branch();
        branch.setId(branchId);
        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        Applicant applicant = new Applicant();
        applicant.setStatus(ApplicantStatus.SCHEDULED);
        Booking booking = new Booking();
        booking.setId(id);
        booking.setSchedule(schedule);
        booking.setApplicant(applicant);
        booking.setStatus(status);
        return booking;
    }
}

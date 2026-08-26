package com.company.iss.booking.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.repository.BookingRescheduleHistoryRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.notification.service.NotificationService;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingWorkflowSecurityTest {

    @Mock NotificationService notificationService;
    @Mock BookingRepository bookingRepository;
    @Mock BookingRescheduleHistoryRepository historyRepository;
    @Mock InterviewEvaluationRepository evaluationRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock ApplicantService applicantService;
    @Mock SecurityService securityService;
    @Mock ApplicationEventPublisher eventPublisher;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(
                notificationService,
                bookingRepository,
                historyRepository,
                evaluationRepository,
                scheduleRepository,
                applicantService,
                securityService,
                eventPublisher
        );
    }

    @Test
    void recruiterCannotConfirmGuessedBookingFromAnotherBranch() {
        when(securityService.getCurrentUser()).thenReturn(recruiter(1L));
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(booking(50L, 2L, BookingStatus.BOOKED)));

        assertThrows(AccessDeniedException.class, () -> service.confirm(50L));

        verify(bookingRepository, never()).save(any());
        verify(notificationService, never()).send(any(), any());
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

        assertThrows(IllegalStateException.class, () -> service.markNoShow(50L));

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

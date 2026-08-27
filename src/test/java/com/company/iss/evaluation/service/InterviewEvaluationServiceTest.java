package com.company.iss.evaluation.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.evaluation.dto.CreateEvaluationCommand;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.repository.PositionOpeningRepository;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewEvaluationServiceTest {

    @Mock InterviewEvaluationRepository evaluationRepository;
    @Mock PositionOpeningRepository positionOpeningRepository;
    @Mock BookingRepository bookingRepository;
    @Mock SecurityService securityService;

    private InterviewEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new InterviewEvaluationService(
                evaluationRepository, positionOpeningRepository, bookingRepository, securityService
        );
    }

    @Test
    void recruiterCannotEvaluateGuessedBookingFromAnotherBranch() {
        User actor = recruiter(1L);
        Booking booking = booking(20L, 2L, BookingStatus.ATTENDED);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(booking));

        assertThrows(AccessDeniedException.class, () -> service.create(command(20L)));

        verify(evaluationRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void attendedBookingCreatesEvaluationWithCurrentActorAndUpdatesStatesOnce() {
        User actor = recruiter(1L);
        Booking booking = booking(20L, 1L, BookingStatus.ATTENDED);
        PositionOpening position = new PositionOpening();
        position.setInterviewedCount(3);
        position.setPassedCount(1);
        booking.getApplicant().setPositionOpening(position);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(booking));
        when(evaluationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InterviewEvaluation result = service.create(command(20L));

        assertSame(actor, result.getEvaluator());
        assertEquals(BookingStatus.PASSED, booking.getStatus());
        assertEquals(ApplicantStatus.PASSED, booking.getApplicant().getStatus());
        assertEquals(4, position.getInterviewedCount());
        assertEquals(2, position.getPassedCount());
        verify(evaluationRepository).existsByBookingId(20L);
        verify(evaluationRepository).save(result);
    }

    @Test
    void invalidStateIsRejectedBeforeAnyEvaluationWrite() {
        User actor = recruiter(1L);
        Booking booking = booking(20L, 1L, BookingStatus.CONFIRMED);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(booking));

        assertThrows(BusinessRuleViolationException.class, () -> service.create(command(20L)));

        verify(evaluationRepository, never()).save(any());
    }

    private CreateEvaluationCommand command(Long bookingId) {
        return new CreateEvaluationCommand(bookingId, 8, 9, 7, InterviewResult.PASS, "Strong result");
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

    private Booking booking(Long bookingId, Long branchId, BookingStatus status) {
        Branch branch = new Branch();
        branch.setId(branchId);
        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        Applicant applicant = new Applicant();
        applicant.setStatus(ApplicantStatus.INTERVIEWED);
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setSchedule(schedule);
        booking.setApplicant(applicant);
        booking.setStatus(status);
        return booking;
    }
}

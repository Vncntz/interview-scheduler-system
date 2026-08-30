package com.company.iss.evaluation.service;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.stream.Stream;

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
                evaluationRepository,
                positionOpeningRepository,
                bookingRepository,
                securityService
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

    @ParameterizedTest
    @MethodSource("allowedStageTransitions")
    void allowedStageTransitionsUpdateApplicantAndBooking(
            InterviewStage stage,
            InterviewResult result,
            ApplicantStatus expectedApplicantStatus,
            BookingStatus expectedBookingStatus
    ) {
        User actor = recruiter(1L);
        Booking booking = booking(20L, 1L, BookingStatus.ATTENDED, stage);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(booking));
        when(evaluationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(command(20L, result));

        assertEquals(expectedApplicantStatus, booking.getApplicant().getStatus());
        assertEquals(expectedBookingStatus, booking.getStatus());
    }

    @ParameterizedTest
    @MethodSource("rejectedStageTransitions")
    void invalidStageTransitionsAreRejectedBeforeWrites(InterviewStage stage, InterviewResult result) {
        User actor = recruiter(1L);
        Booking booking = booking(20L, 1L, BookingStatus.ATTENDED, stage);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(booking));

        assertThrows(BusinessRuleViolationException.class, () -> service.create(command(20L, result)));

        assertEquals(BookingStatus.ATTENDED, booking.getStatus());
        assertEquals(ApplicantStatus.INTERVIEWED, booking.getApplicant().getStatus());
        verify(evaluationRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    private CreateEvaluationCommand command(Long bookingId) {
        return command(bookingId, InterviewResult.PASS);
    }

    private CreateEvaluationCommand command(Long bookingId, InterviewResult result) {
        return new CreateEvaluationCommand(bookingId, 8, 9, 7, result, "Strong result");
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
        return booking(bookingId, branchId, status, InterviewStage.INITIAL);
    }

    private Booking booking(Long bookingId, Long branchId, BookingStatus status, InterviewStage stage) {
        Branch branch = new Branch();
        branch.setId(branchId);
        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        Applicant applicant = new Applicant();
        applicant.setStatus(ApplicantStatus.INTERVIEWED);
        Booking booking = Booking.forInterviewStage(stage);
        booking.setId(bookingId);
        booking.setSchedule(schedule);
        booking.setApplicant(applicant);
        booking.setStatus(status);
        return booking;
    }

    private static Stream<Arguments> allowedStageTransitions() {
        return Stream.of(
                Arguments.of(
                        InterviewStage.INITIAL,
                        InterviewResult.FOR_FINAL_INTERVIEW,
                        ApplicantStatus.FOR_FINAL_INTERVIEW,
                        BookingStatus.FOR_FINAL_INTERVIEW
                ),
                Arguments.of(
                        InterviewStage.INITIAL,
                        InterviewResult.FOR_CLIENT_INTERVIEW,
                        ApplicantStatus.FOR_CLIENT_INTERVIEW,
                        BookingStatus.FOR_CLIENT_INTERVIEW
                ),
                Arguments.of(
                        InterviewStage.FINAL,
                        InterviewResult.FOR_CLIENT_INTERVIEW,
                        ApplicantStatus.FOR_CLIENT_INTERVIEW,
                        BookingStatus.FOR_CLIENT_INTERVIEW
                ),
                Arguments.of(
                        InterviewStage.CLIENT,
                        InterviewResult.PASS,
                        ApplicantStatus.PASSED,
                        BookingStatus.PASSED
                ),
                Arguments.of(
                        InterviewStage.CLIENT,
                        InterviewResult.FAIL,
                        ApplicantStatus.FAILED,
                        BookingStatus.FAILED
                )
        );
    }

    private static Stream<Arguments> rejectedStageTransitions() {
        return Stream.of(
                Arguments.of(InterviewStage.FINAL, InterviewResult.FOR_FINAL_INTERVIEW),
                Arguments.of(InterviewStage.CLIENT, InterviewResult.FOR_FINAL_INTERVIEW),
                Arguments.of(InterviewStage.CLIENT, InterviewResult.FOR_CLIENT_INTERVIEW)
        );
    }
}

package com.company.iss.evaluation.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.service.ApplicantService;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewEvaluationServiceTest {

    @Mock InterviewEvaluationRepository evaluationRepository;
    @Mock PositionOpeningRepository positionOpeningRepository;
    @Mock BookingRepository bookingRepository;
    @Mock ApplicantService applicantService;
    @Mock SecurityService securityService;

    private InterviewEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new InterviewEvaluationService(
                evaluationRepository,
                positionOpeningRepository,
                bookingRepository,
                applicantService,
                securityService,
                com.company.iss.shared.time.BusinessTimeTestFactory.at(
                        java.time.Instant.parse("2026-09-01T00:00:00.123456789Z"))
        );
    }

    @Test
    void recruiterCannotEvaluateGuessedBookingFromAnotherBranch() {
        User actor = recruiter(1L);
        Booking booking = booking(20L, 1L, BookingStatus.ATTENDED);
        booking.getApplicant().setBranch(branch(2L));
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(bookingRepository.findApplicantIdById(20L)).thenReturn(Optional.of(booking.getApplicant().getId()));
        when(applicantService.findForWorkflowUpdate(booking.getApplicant().getId(), actor))
                .thenThrow(new AccessDeniedException("out of scope"));

        assertThrows(AccessDeniedException.class, () -> service.create(command(20L)));

        verify(bookingRepository, never()).findByIdForUpdate(any());
        verify(evaluationRepository, never()).append(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void newBranchRecruiterCanEvaluateTransferredApplicantOnHistoricalSchedule() {
        User actor = recruiter(2L);
        Booking booking = booking(20L, 1L, BookingStatus.ATTENDED);
        booking.getApplicant().setBranch(branch(2L));
        when(securityService.requireOperationsUser()).thenReturn(actor);
        stubBookingAccess(actor, booking);
        when(evaluationRepository.append(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(command(20L));

        assertEquals(BookingStatus.PASSED, booking.getStatus());
        assertEquals(ApplicantStatus.PASSED, booking.getApplicant().getStatus());
        assertEquals(1L, booking.getSchedule().getBranch().getId());
        assertEquals(2L, booking.getApplicant().getBranch().getId());
    }

    @Test
    void adminCanEvaluateTransferredApplicantOrganizationWide() {
        User actor = new User();
        actor.setRole(Role.ADMIN);
        actor.setActive(true);
        Booking booking = booking(20L, 1L, BookingStatus.ATTENDED);
        booking.getApplicant().setBranch(branch(2L));
        when(securityService.requireOperationsUser()).thenReturn(actor);
        stubBookingAccess(actor, booking);
        when(evaluationRepository.append(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(command(20L));

        assertEquals(BookingStatus.PASSED, booking.getStatus());
        assertEquals(ApplicantStatus.PASSED, booking.getApplicant().getStatus());
    }

    @Test
    void attendedBookingCreatesEvaluationWithCurrentActorAndUpdatesStatesOnce() {
        User actor = recruiter(1L);
        Booking booking = booking(20L, 1L, BookingStatus.ATTENDED);
        PositionOpening position = new PositionOpening();
        position.setInterviewEvaluationCount(3);
        position.setPassedCount(1);
        booking.getApplicant().setPositionOpening(position);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        stubBookingAccess(actor, booking);
        when(evaluationRepository.append(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InterviewEvaluation result = service.create(command(20L));

        assertSame(actor, result.getEvaluator());
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 0, 0, 123_456_000), result.getEvaluationDate());
        assertEquals(BookingStatus.PASSED, booking.getStatus());
        assertEquals(ApplicantStatus.PASSED, booking.getApplicant().getStatus());
        assertEquals(4, position.getInterviewEvaluationCount());
        assertEquals(2, position.getPassedCount());
        verify(evaluationRepository).existsByBookingId(20L);
        verify(evaluationRepository).append(result);
        var lockOrder = inOrder(applicantService, bookingRepository);
        lockOrder.verify(applicantService).findForWorkflowUpdate(booking.getApplicant().getId(), actor);
        lockOrder.verify(bookingRepository).findByIdForUpdate(20L);
    }

    @Test
    void initialProgressionThenFinalPassCountsTwoEvaluationsAndOnePass() {
        User actor = recruiter(1L);
        PositionOpening position = positionWithCounters(0, 0);
        Applicant applicant = applicantFor(position);
        Booking initialBooking = booking(20L, 1L, BookingStatus.ATTENDED, InterviewStage.INITIAL);
        initialBooking.setApplicant(applicant);
        Booking finalBooking = booking(21L, 1L, BookingStatus.ATTENDED, InterviewStage.FINAL);
        finalBooking.setApplicant(applicant);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        stubBookingAccess(actor, initialBooking);
        stubBookingAccess(actor, finalBooking);
        when(evaluationRepository.append(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(command(20L, InterviewResult.FOR_FINAL_INTERVIEW));

        assertEquals(1, position.getInterviewEvaluationCount());
        assertEquals(0, position.getPassedCount());
        assertEquals(ApplicantStatus.FOR_FINAL_INTERVIEW, applicant.getStatus());

        applicant.setStatus(ApplicantStatus.INTERVIEWED);
        service.create(command(21L, InterviewResult.PASS));

        assertEquals(2, position.getInterviewEvaluationCount());
        assertEquals(1, position.getPassedCount());
        assertEquals(ApplicantStatus.PASSED, applicant.getStatus());
    }

    @Test
    void threeStageProgressionCountsThreeEvaluationsAndOnePass() {
        User actor = recruiter(1L);
        PositionOpening position = positionWithCounters(0, 0);
        Applicant applicant = applicantFor(position);
        Booking initialBooking = booking(20L, 1L, BookingStatus.ATTENDED, InterviewStage.INITIAL);
        initialBooking.setApplicant(applicant);
        Booking finalBooking = booking(21L, 1L, BookingStatus.ATTENDED, InterviewStage.FINAL);
        finalBooking.setApplicant(applicant);
        Booking clientBooking = booking(22L, 1L, BookingStatus.ATTENDED, InterviewStage.CLIENT);
        clientBooking.setApplicant(applicant);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        stubBookingAccess(actor, initialBooking);
        stubBookingAccess(actor, finalBooking);
        stubBookingAccess(actor, clientBooking);
        when(evaluationRepository.append(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(command(20L, InterviewResult.FOR_FINAL_INTERVIEW));
        assertEquals(1, position.getInterviewEvaluationCount());
        assertEquals(0, position.getPassedCount());

        applicant.setStatus(ApplicantStatus.INTERVIEWED);
        service.create(command(21L, InterviewResult.FOR_CLIENT_INTERVIEW));
        assertEquals(2, position.getInterviewEvaluationCount());
        assertEquals(0, position.getPassedCount());

        applicant.setStatus(ApplicantStatus.INTERVIEWED);
        service.create(command(22L, InterviewResult.PASS));

        assertEquals(3, position.getInterviewEvaluationCount());
        assertEquals(1, position.getPassedCount());
        assertEquals(ApplicantStatus.PASSED, applicant.getStatus());
    }

    @Test
    void failedEvaluationIncrementsEvaluationCountWithoutIncrementingPassedCount() {
        User actor = recruiter(1L);
        Booking booking = booking(20L, 1L, BookingStatus.ATTENDED);
        PositionOpening position = positionWithCounters(4, 2);
        booking.getApplicant().setPositionOpening(position);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        stubBookingAccess(actor, booking);
        when(evaluationRepository.append(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(command(20L, InterviewResult.FAIL));

        assertEquals(5, position.getInterviewEvaluationCount());
        assertEquals(2, position.getPassedCount());
        assertEquals(ApplicantStatus.FAILED, booking.getApplicant().getStatus());
    }

    @Test
    void duplicateEvaluationIsRejectedWithoutDoubleCounting() {
        User actor = recruiter(1L);
        Booking booking = booking(20L, 1L, BookingStatus.ATTENDED);
        PositionOpening position = positionWithCounters(0, 0);
        booking.getApplicant().setPositionOpening(position);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        stubBookingAccess(actor, booking);
        when(evaluationRepository.existsByBookingId(20L)).thenReturn(false, true);
        when(evaluationRepository.append(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(command(20L, InterviewResult.PASS));
        booking.setStatus(BookingStatus.ATTENDED);

        assertThrows(BusinessRuleViolationException.class,
                () -> service.create(command(20L, InterviewResult.PASS)));

        assertEquals(1, position.getInterviewEvaluationCount());
        assertEquals(1, position.getPassedCount());
        verify(evaluationRepository, times(1)).append(any());
        verify(positionOpeningRepository, times(1)).save(position);
    }

    @Test
    void invalidStateIsRejectedBeforeAnyEvaluationWrite() {
        User actor = recruiter(1L);
        Booking booking = booking(20L, 1L, BookingStatus.CONFIRMED);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        stubBookingAccess(actor, booking);

        assertThrows(BusinessRuleViolationException.class, () -> service.create(command(20L)));

        verify(evaluationRepository, never()).append(any());
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
        stubBookingAccess(actor, booking);
        when(evaluationRepository.append(any())).thenAnswer(invocation -> invocation.getArgument(0));

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
        stubBookingAccess(actor, booking);

        assertThrows(BusinessRuleViolationException.class, () -> service.create(command(20L, result)));

        assertEquals(BookingStatus.ATTENDED, booking.getStatus());
        assertEquals(ApplicantStatus.INTERVIEWED, booking.getApplicant().getStatus());
        verify(evaluationRepository, never()).append(any());
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
        applicant.setId(bookingId + 100L);
        applicant.setBranch(branch);
        applicant.setStatus(ApplicantStatus.INTERVIEWED);
        Booking booking = Booking.forInterviewStage(stage);
        booking.setId(bookingId);
        booking.setSchedule(schedule);
        booking.setApplicant(applicant);
        booking.setStatus(status);
        return booking;
    }

    private Applicant applicantFor(PositionOpening position) {
        Applicant applicant = new Applicant();
        applicant.setId(120L);
        applicant.setBranch(branch(1L));
        applicant.setStatus(ApplicantStatus.INTERVIEWED);
        applicant.setPositionOpening(position);
        return applicant;
    }

    private Branch branch(Long branchId) {
        Branch branch = new Branch();
        branch.setId(branchId);
        return branch;
    }

    private void stubBookingAccess(User actor, Booking booking) {
        Long bookingId = booking.getId();
        Applicant applicant = booking.getApplicant();
        when(bookingRepository.findApplicantIdById(bookingId)).thenReturn(Optional.of(applicant.getId()));
        when(applicantService.findForWorkflowUpdate(applicant.getId(), actor)).thenReturn(applicant);
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
    }

    private PositionOpening positionWithCounters(int interviewEvaluationCount, int passedCount) {
        PositionOpening position = new PositionOpening();
        position.setInterviewEvaluationCount(interviewEvaluationCount);
        position.setPassedCount(passedCount);
        return position;
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
